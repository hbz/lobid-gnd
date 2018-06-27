/* Copyright 2014-2018, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHits;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

import models.GndOntology;
import modules.IndexComponent;
import play.Logger;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

/**
 * OpenRefine reconciliation service controller.
 * 
 * Serves reconciliation service meta data and multi query requests.
 * 
 * See https://github.com/OpenRefine/OpenRefine/wiki/Reconciliation and
 * https://github.com/OpenRefine/OpenRefine/wiki/Reconciliation-Service-API
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class Reconcile extends Controller {

	@Inject
	IndexComponent index;

	private static final JsonNode TYPES = Json.toJson(HomeController.CONFIG.getStringList("topLevelTypes").stream()
			.map(t -> ImmutableMap.of("id", t, "name", GndOntology.label(t))));

	/**
	 * @param callback
	 *            The name of the JSONP function to wrap the response in
	 * @return OpenRefine reconciliation endpoint meta data, wrapped in
	 *         `callback`
	 */
	public Result meta(String callback) {
		ObjectNode result = Json.newObject();
		result.put("name", "lobid-gnd reconciliation");
		result.put("identifierSpace", "http://lobid.org/gnd");
		result.put("schemaSpace", "http://lobid.org/gnd");
		result.set("defaultTypes", TYPES);
		result.set("view", Json.newObject()//
				.put("url", "http://lobid.org/gnd/{{id}}"));
		return callback.isEmpty() ? ok(result)
				: ok(String.format("/**/%s(%s);", callback, result.toString())).as("application/json");
	}

	/** @return Reconciliation data for the queries in the request */
	public Result reconcile() {
		JsonNode request = Json.parse(request().body().asFormUrlEncoded().get("queries")[0]);
		Iterator<Entry<String, JsonNode>> inputQueries = request.fields();
		ObjectNode response = Json.newObject();
		while (inputQueries.hasNext()) {
			Entry<String, JsonNode> inputQuery = inputQueries.next();
			Logger.debug("q: " + inputQuery);
			SearchResponse searchResponse = executeQuery(inputQuery, buildQueryString(inputQuery));
			List<JsonNode> results = mapToResults(mainQuery(inputQuery), searchResponse.getHits());
			ObjectNode resultsForInputQuery = Json.newObject();
			resultsForInputQuery.set("result", Json.toJson(results));
			Logger.debug("r: " + resultsForInputQuery);
			response.set(inputQuery.getKey(), resultsForInputQuery);
		}
		return ok(response);
	}

	private List<JsonNode> mapToResults(String mainQuery, SearchHits searchHits) {
		return Arrays.asList(searchHits.getHits()).stream().map(hit -> {
			Map<String, Object> map = hit.getSource();
			ObjectNode resultForHit = Json.newObject();
			resultForHit.put("id", hit.getId());
			Object nameObject = map.get("preferredName");
			String name = nameObject == null ? "" : nameObject + "";
			resultForHit.put("name", name);
			resultForHit.put("score", hit.getScore());
			resultForHit.put("match", mainQuery.equalsIgnoreCase(name));
			resultForHit.set("type", TYPES);
			return resultForHit;
		}).collect(Collectors.toList());
	}

	private SearchResponse executeQuery(Entry<String, JsonNode> entry, String queryString) {
		JsonNode limitNode = entry.getValue().get("limit");
		int limit = limitNode == null ? -1 : limitNode.asInt();
		JsonNode typeNode = entry.getValue().get("type");
		String filter = typeNode == null ? "" : "type:" + typeNode.asText();
		return index.query(queryString, filter, 0, limit);
	}

	private String buildQueryString(Entry<String, JsonNode> entry) {
		String queryString = mainQuery(entry);
		JsonNode props = entry.getValue().get("properties");
		if (props != null) {
			for (JsonNode p : props) {
				queryString += " " + p.get("v").asText();
			}
		}
		return queryString;
	}

	private String mainQuery(Entry<String, JsonNode> entry) {
		return entry.getValue().get("query").asText();
	}

}
