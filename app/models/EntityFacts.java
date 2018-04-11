package models;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.HomeController;
import play.Logger;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;

/**
 * Representation of an EntityFacts entity.
 * 
 * See http://hub.culturegraph.org/entityfacts
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class EntityFacts {

	public List<Map<String, Object>> sameAs;
	public Map<String, Object> depiction;
	public String imageAttribution;

	public static EntityFacts entity(WSClient client, String id) {
		EntityFacts entity = Json.fromJson(json(client, id), EntityFacts.class);
		if (entity.getImage().url.contains("File:"))
			entity.imageAttribution = attribution(client,
					entity.getImage().url.substring(entity.getImage().url.indexOf("File:") + 5).split("\\?")[0]);
		return entity;
	}

	public List<Link> getLinks() {
		return sameAs == null ? Collections.emptyList() : sameAs.stream().map(map -> {
			String url = map.get("@id").toString();
			Object icon = null;
			Object label = null;
			Object collection = map.get("collection");
			if (collection != null) {
				@SuppressWarnings("unchecked")
				Map<String, Object> collectionMap = (Map<String, Object>) collection;
				icon = collectionMap.get("icon");
				label = collectionMap.get("name");
			}
			return new Link(url, icon == null ? "" : icon.toString(), label == null ? "" : label.toString());
		}).collect(Collectors.toList());
	}

	public Link getImage() {
		if (depiction != null) {
			String url = depiction.get("url").toString();
			String image = depiction.get("@id").toString();
			Object thumbnailObject = depiction.get("thumbnail");
			if (thumbnailObject != null) {
				@SuppressWarnings("unchecked")
				String thumbnailString = ((Map<String, Object>) thumbnailObject).get("@id").toString();
				image = thumbnailString;
			}
			return new Link(url, image, imageAttribution != null ? imageAttribution : url);
		}
		return new Link("", "", ""); // TODO: null better?
	}

	public static class Link implements Comparable<Link> {
		public String url;
		public String image;
		public String label;

		Link(String url, String icon, String label) {
			this.url = url;
			this.image = icon;
			this.label = label.replace("Gemeinsame Normdatei (GND) im Katalog der Deutschen Nationalbibliothek",
					"Deutsche Nationalbibliothek (DNB)");
		}

		@Override
		public int hashCode() {
			return url.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Link that = (Link) obj;
			return that.url.equals(this.url);
		}

		@Override
		public String toString() {
			return "Link [url=" + url + ", icon=" + image + ", label=" + label + "]";
		}

		@Override
		public int compareTo(Link that) {
			return that.url.compareTo(this.url);
		}
	}

	private static final String URI_PREFIX = "http://d-nb.info/gnd/";

	private static String attribution(WSClient client, String url) {
		try {
			return requestInfo(client, url).thenApply(info -> {
				String attribution = createAttribution(url, info.asJson());
				return String.format("Bildquelle: %s", attribution);
			}).toCompletableFuture().get();
		} catch (UnsupportedEncodingException | InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static CompletionStage<WSResponse> requestInfo(WSClient client, String imageName)
			throws UnsupportedEncodingException {
		String imageId = "File:" + URLDecoder.decode(imageName, StandardCharsets.UTF_8.name());
		return client.url("https://commons.wikimedia.org/w/api.php")//
				.addQueryParameter("action", "query")//
				.addQueryParameter("format", "json")//
				.addQueryParameter("prop", "imageinfo")//
				.addQueryParameter("iiprop", "extmetadata")//
				.addQueryParameter("titles", imageId).get();
	}

	private static String createAttribution(String fileName, JsonNode info) {
		String artist = findText(info, "Artist");
		String licenseText = findText(info, "LicenseShortName");
		String licenseUrl = findText(info, "LicenseUrl");
		String fileSourceUrl = "https://commons.wikimedia.org/wiki/File:" + fileName;
		return String.format(
				(artist.isEmpty() ? "%s" : "%s | ") + "<a href='%s'>Wikimedia Commons</a> | <a href='%s'>%s</a>",
				artist, fileSourceUrl, licenseUrl.isEmpty() ? fileSourceUrl : licenseUrl, licenseText);
	}

	private static String findText(JsonNode info, String field) {
		JsonNode node = info.findValue(field);
		return node != null ? node.get("value").asText().replace("\n", " ").trim() : "";
	}

	private static JsonNode json(WSClient client, String gndNumber) {
		gndNumber = gndNumber.replace(URI_PREFIX, "");
		String queryString = String.format("@id:\"" + URI_PREFIX + "%s\"", gndNumber);
		try {
			JsonNode result = client//
					.url(HomeController.config("data.entityfacts"))//
					.addQueryParameter("q", queryString)//
					.setFollowRedirects(true).get().thenApply(r -> {
						String body = r.getBody();
						JsonNode hits = Json.parse(body).findValue("hits");
						long total = hits.findValue("total").asLong();
						if (total > 0) {
							return hits.findValue("hits").elements().next().findValue("_source");
						} else {
							Logger.debug("No result for query {}, response {}", queryString, body);
							return Json.newObject();
						}
					}).toCompletableFuture().get();
			// TODO: handle java.net.ConnectException: Connection refused
			return result;
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		return Json.newObject();
	}

}
