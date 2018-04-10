package models;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import controllers.HomeController;
import play.Logger;
import play.libs.Json;
import play.libs.ws.WSClient;

/**
 * Representation of an EntityFacts entity.
 * 
 * See http://hub.culturegraph.org/entityfacts
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class EntityFacts {

	private static final String URI_PREFIX = "http://d-nb.info/gnd/";
	public List<Map<String, Object>> sameAs;
	public Map<String, Object> depiction;

	public static EntityFacts entity(WSClient client, String id) {
		return Json.fromJson(json(client, id), EntityFacts.class);
	}

	public List<String> getLinks() {
		return sameAs == null ? Collections.emptyList()
				: sameAs.stream().map(map -> map.get("@id").toString()).collect(Collectors.toList());
	}

	public String getImage() {
		return depiction == null ? "" : depiction.get("@id").toString();
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
