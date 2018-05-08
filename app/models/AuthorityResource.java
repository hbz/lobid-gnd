package models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.common.geo.GeoPoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;

import controllers.HomeController;
import play.Logger;
import play.libs.Json;

public class AuthorityResource {

	private static final int SHORTEN = 10;
	public final static String DNB_PREFIX = "http://d-nb.info/gnd/";
	private static final List<String> SKIP = Arrays.asList(//
			// handled explicitly:
			"@context", "id", "type", "depiction", "sameAs", "preferredName", "hasGeometry", //
			// don't display:
			"variantNameEntityForThePerson", "deprecatedUri", "oldAuthorityNumber", "wikipedia");

	private String id;
	private List<String> type;
	public List<Map<String, Object>> hasGeometry;
	public String preferredName;
	public List<Map<String, Object>> depiction;
	public List<Map<String, Object>> sameAs;
	public List<String> creatorOf;
	public String imageAttribution;
	private JsonNode json;

	public AuthorityResource(JsonNode json) {
		this.json = json;
		this.id = json.get("id").textValue();
		this.type = get("type");
		this.hasGeometry = get("hasGeometry");
		this.preferredName = json.get("preferredName").textValue();
		this.depiction = get("depiction");
		this.sameAs = get("sameAs");
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> get(String field) {
		JsonNode fieldContent = json.get(field);
		return fieldContent == null ? Collections.emptyList() : Json.fromJson(fieldContent, List.class);
	}

	public String getId() {
		return id.substring(DNB_PREFIX.length());
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<String> getType() {
		return type.stream().filter(t -> !t.equals("AuthorityResource")).collect(Collectors.toList());
	}

	public void setType(List<String> type) {
		this.type = type;
	}

	@Override
	public String toString() {
		return "AuthorityResource [id=" + id + "]";
	}

	public String title() {
		return preferredName;
	}

	public GeoPoint location() {
		if (hasGeometry.isEmpty())
			return null;
		@SuppressWarnings("unchecked")
		String geoString = ((List<Map<String, Object>>) hasGeometry.get(0).get("asWKT")).get(0).get("@value")
				.toString();
		List<Double> lonLat = scanGeoCoordinates(geoString);
		if (lonLat.size() != 2) {
			throw new IllegalArgumentException("Could not scan geo location from: " + geoString + ", got: " + lonLat);
		}
		return new GeoPoint(lonLat.get(1), lonLat.get(0));
	}

	public List<Pair<String, String>> generalFields() {
		List<Pair<String, String>> fields = new ArrayList<>();
		addValues("type", typeLinks(), fields);
		addValues("creatorOf", creatorOf, fields);
		addRest(fields);
		return fields;
	}

	public List<Pair<String, String>> additionalLinks() {
		ArrayList<LinkWithImage> links = new ArrayList<>(new TreeSet<>(getLinks()));
		List<Pair<String, String>> result = new ArrayList<>();
		if (!links.isEmpty()) {
			String field = "sameAs";
			String value = IntStream.range(0, links.size()).mapToObj(i -> html(field, links, i))
					.collect(Collectors.joining(" | "));
			result.add(Pair.of(field, value));
		}
		return result;
	}

	public LinkWithImage getImage() {
		if (depiction != null && depiction.size() > 0) {
			String url = depiction.get(0).get("url").toString();
			String image = depiction.get(0).get("id").toString();
			Object thumbnail = depiction.get(0).get("thumbnail");
			image = thumbnail != null ? thumbnail.toString() : image;
			return new LinkWithImage(url, image, imageAttribution != null ? imageAttribution : url);
		}
		return new LinkWithImage("", "", "");
	}

	private List<Double> scanGeoCoordinates(String geoString) {
		List<Double> lonLat = new ArrayList<Double>();
		try (@SuppressWarnings("resource") // it's the same scanner!
		Scanner s = new Scanner(geoString).useLocale(Locale.US)) {
			while (s.hasNext()) {
				if (s.hasNextDouble()) {
					lonLat.add(s.nextDouble());
				} else {
					s.next();
				}
			}
		}
		return lonLat;
	}

	private void addRest(List<Pair<String, String>> fields) {
		Lists.newArrayList(json.fieldNames()).stream().filter(k -> !SKIP.contains(k)).forEach(key -> {
			JsonNode node = json.get(key);
			switch (node.getNodeType()) {
			case STRING:
				addValues(key, Arrays.asList(json.get(key).textValue()), fields);
				break;
			case ARRAY:
				addArray(key, Lists.newArrayList(node.elements()), fields);
				break;
			default:
				Logger.warn("Unexpected JsonNodeType for: {}", node);
				break;
			}
		});
	}

	private void addArray(String key, List<JsonNode> list, List<Pair<String, String>> fields) {
		if (list.size() > 0) {
			JsonNode node = list.get(0);
			switch (node.getNodeType()) {
			case STRING:
				addValues(key, fields);
				break;
			case OBJECT:
				addIds(key, fields);
				break;
			default:
				Logger.warn("Unexpected JsonNodeType for: {}", node);
				break;
			}
		}
	}

	private void addIds(String field, List<Pair<String, String>> result) {
		List<Map<String, Object>> list = get(field);
		add(field, list, result, i -> {
			String id = list.get(i).get("id").toString();
			String label = list.get(i).get("label").toString();
			return process(field, id, label, i, list.size());
		});
	}

	private void addValues(String field, List<Pair<String, String>> result) {
		addValues(field, get(field), result);
	}

	private void addValues(String field, List<String> list, List<Pair<String, String>> result) {
		add(field, list, result, i -> process(field, list.get(i), list.get(i), i, list.size()));
	}

	private void add(String field, List<?> list, List<Pair<String, String>> result,
			IntFunction<? extends String> function) {
		try {
			if (list != null && list.size() > 0) {
				String value = IntStream.range(0, list.size()).mapToObj(function).collect(Collectors.joining(" | "));
				result.add(Pair.of(field, value));
			}
		} catch (Exception e) {
			Logger.warn("Could not add IDs for field {} in {}", field, json);
			e.printStackTrace();
		}
	}

	private List<String> typeLinks() {
		List<String> subTypes = getType().stream()
				.filter(t -> HomeController.CONFIG.getObject("types").keySet().contains(t))
				.collect(Collectors.toList());
		List<String> typeLinks = (subTypes.isEmpty() ? getType() : subTypes).stream()
				.map(t -> String.format("<a href='%s'>%s</a>",
						controllers.routes.HomeController.search("", "+(type:" + t + ")", 0, 10, "").toString(),
						models.GndOntology.label(t)))
				.collect(Collectors.toList());
		return typeLinks;
	}

	private List<LinkWithImage> getLinks() {
		return sameAs == null ? Collections.emptyList() : sameAs.stream().map(map -> {
			String url = map.get("id").toString();
			Object icon = null;
			Object label = null;
			Object collection = map.get("collection");
			if (collection != null) {
				@SuppressWarnings("unchecked")
				Map<String, Object> collectionMap = (Map<String, Object>) collection;
				icon = collectionMap.get("icon");
				label = collectionMap.get("name");
			}
			return new LinkWithImage(url, icon == null ? "" : icon.toString(), label == null ? "" : label.toString());
		}).collect(Collectors.toList());
	}

	private String html(String field, ArrayList<LinkWithImage> links, int i) {
		LinkWithImage link = links.get(i);
		String result = String.format("<a href='%s'><img src='%s' style='height:1em'/>&nbsp;%s</a>", //
				link.url, link.image, link.label);
		return withDefaultHidden(field, links.size(), i, result);
	}

	private String process(String field, String value, String label, int i, int size) {
		String result = label;
		if ("creatorOf".equals(field)) {
			result = String.format("<a href='%s'>%s</a>",
					controllers.routes.HomeController.authority(value.replace(DNB_PREFIX, ""), null), label);
		} else if (Arrays.asList("wikipedia", "sameAs", "depiction", "homepage").contains(field)) {
			result = String.format("<a href='%s'>%s</a>", value, value);
		} else if (value.startsWith("http")) {
			String link = value.startsWith(DNB_PREFIX) ? controllers.routes.HomeController
					.authorityDotFormat(value.replace(DNB_PREFIX, ""), "html").toString() : value;
			String search = controllers.routes.HomeController.search(field + ".id:\"" + value + "\"", "", 0, 10, "html")
					.toString();
			String entityLink = String.format(
					"<a id='%s-%s' title='Linked-Data-Quelle zu \"%s\" anzeigen' href='%s'>%s</a>", //
					field, i, label, link, label);
			String searchLink = String.format(
					"<a title='Weitere Einträge mit %s \"%s\" suchen' href='%s'>"
							+ "<i class='octicon octicon-search' aria-hidden='true'></i></a>",
					GndOntology.label(field), label, search);
			result = entityLink + "&nbsp;" + searchLink;
		}
		return withDefaultHidden(field, size, i, result);
	}

	private String withDefaultHidden(String field, int size, int i, String result) {
		if (i == SHORTEN) {
			result = String.format("<span id='%s-hide-by-default' style='display: none;'>", field.replace(".id", ""))
					+ result;
		}
		if (i >= SHORTEN && i == size - 1) {
			result = result + "</span>";
		}
		return result;
	}
}
