/* Copyright 2014-2018, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHits;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

import models.AuthorityResource;
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
		result.set("preview",
				Json.newObject()//
						.put("height", 100)//
						.put("width", 320)//
						.put("url", HomeController.config("host") + "/gnd/{{id}}.preview"));
		result.set("extend",
				Json.toJson(ImmutableMap.of(//
						// TODO: implement property settings
						"property_settings", Json.newArray(), //
						"propose_properties",
						Json.newObject()//
								.put("service_url", HomeController.config("host"))//
								.put("service_path", routes.Reconcile.properties("", "", "").toString()))));
		return callback.isEmpty() ? ok(result)
				: ok(String.format("/**/%s(%s);", callback, result.toString())).as("application/json");
	}

	/**
	 * @param callback
	 *            The name of the JSONP function to wrap the response in
	 * @param callback
	 *            The type used in reconciliation
	 * @return Property proposal protocol data
	 */
	public Result properties(String callback, String type, String limit) {
		long L = limit.isEmpty() ? Long.MAX_VALUE : Long.parseLong(limit);
		List<Object> properties = HomeController.CONFIG.getStringList("field.order").stream()
				.map(field -> ImmutableMap.of("id", field, "name", GndOntology.label(field))).limit(L)
				.collect(Collectors.toList());
		// TODO: limit proposed properties based on type
		JsonNode response = (limit.isEmpty() ? Json.newObject() : Json.newObject().put("limit", L))//
				.put("type", type)//
				.set("properties", Json.toJson(properties));
		return callback.isEmpty() ? ok(response)
				: ok(String.format("/**/%s(%s);", callback, response.toString())).as("application/json");
	}

	/** @return Reconciliation data for the queries in the request */
	public Result reconcile() {
		Map<String, String[]> body = request().body().asFormUrlEncoded();
		return body.containsKey("extend") ? extend(body.get("extend")[0]) : queries(body.get("queries")[0]);
	}

	private Result queries(String src) {
		JsonNode request = Json.parse(src);
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

	private Result extend(String src) {
		JsonNode request = Json.parse(src);
		@SuppressWarnings("unchecked")
		List<HashMap<String, Object>> properties = Json.fromJson(request.get("properties"), List.class);
		List<String> propertyIds = properties.stream().map((HashMap<String, Object> m) -> m.get("id").toString())
				.collect(Collectors.toList());
		ObjectNode rows = Json.newObject();
		request.get("ids").elements().forEachRemaining(entityId -> {
			ObjectNode propertiesForId = Json.newObject();
			propertyIds.stream().forEach(propertyId -> {
				propertiesForId.set(propertyId, Json.toJson(propertyValues(entityId, propertyId)));
			});
			rows.set(entityId.asText(), propertiesForId);
		});
		List<ObjectNode> meta = propertyIds.stream()
				.map(propertyId -> Json.newObject().put("id", propertyId).put("name", GndOntology.label(propertyId)))
				.collect(Collectors.toList());
		return ok(Json.toJson(ImmutableMap.of("meta", Json.toJson(meta), "rows", rows)));
	}

	private List<Map<String, Object>> propertyValues(JsonNode entityId, String propertyId) {
		Map<String, Object> entity = getAuthorityResource(entityId.asText());
		List<Map<String, Object>> result = new ArrayList<>();
		if (entity == null) {
			result.add(ImmutableMap.of());
		} else {
			JsonNode propertyJson = Json.toJson(entity.get(propertyId));
			ArrayNode values = propertyJson == null ? Json.newArray().add(Json.newObject())
					: (propertyJson.isArray() ? (ArrayNode) propertyJson : Json.newArray().add(propertyJson));
			values.elements().forEachRemaining(node -> {
				if (node.isTextual()) {
					result.add(ImmutableMap.of("str", node.asText()));
				} else if (node.isObject()) {
					addValueForObject(result, node);
				} else {
					result.add(ImmutableMap.of());
				}
			});
		}
		return result;
	}

	private void addValueForObject(List<Map<String, Object>> result, JsonNode node) {
		if (!node.elements().hasNext()) {
			result.add(ImmutableMap.of());
			return;
		}
		String id = node.get("id").asText();
		String label = node.get("label").asText();
		if (id.startsWith(AuthorityResource.DNB_PREFIX)) {
			/*
			 * See https://github.com/OpenRefine/OpenRefine/ wiki/
			 * Data-Extension-API#data-extension- protocol: "a reconciled value
			 * (from the same reconciliation service)"
			 */
			result.add(ImmutableMap.of("id", id.substring(AuthorityResource.DNB_PREFIX.length()), "name", label));
		} else {
			result.add(ImmutableMap.of("str", label));
		}
	}

	private Map<String, Object> getAuthorityResource(String id) {
		GetResponse response = index.client()
				.prepareGet(HomeController.config("index.name"), HomeController.config("index.type"), id).get();
		if (!response.isExists()) {
			return null;
		}
		return response.getSource();
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
