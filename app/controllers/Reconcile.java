/* Copyright 2017-2018 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package controllers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
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

	private static final JsonNode TYPES = Json
			.toJson(HomeController.CONFIG.getStringList("topLevelTypes").stream().map(t -> {
				String type = t.equals("Person") ? "DifferentiatedPerson" : t;
				return ImmutableMap.of("id", type, "name", GndOntology.label(type));
			}));

	/**
	 * @param callback
	 *            The name of the JSONP function to wrap the response in
	 * @param queries
	 *            The queries. If this and extend are empty, return service
	 *            metadata
	 * @param extend
	 *            The extension data. If this and queries are empty, return
	 *            service metadata
	 * @return OpenRefine reconciliation results (if queries is not empty), data
	 *         extension information (if extend is not empty), or endpoint meta
	 *         data (if queries and extend are empty), wrapped in `callback`
	 */
	public Result main(String callback, String queries, String extend) {
		ObjectNode result = queries.isEmpty() && extend.isEmpty() ? metadata()
				: (!queries.isEmpty() ? queries(queries) : extend(extend));
		response().setHeader("Access-Control-Allow-Origin", "*");
		return callback.isEmpty() ? ok(result)
				: ok(String.format("/**/%s(%s);", callback, result.toString())).as("application/json");
	}

	private ObjectNode metadata() {
		final String host = HomeController.config("host");
		ObjectNode result = Json.newObject();
		result.put("name", "GND reconciliation for OpenRefine");
		result.put("identifierSpace", host + "/gnd");
		result.put("schemaSpace", host + "/gnd");
		result.set("defaultTypes", TYPES);
		result.set("view", Json.newObject()//
				.put("url", host + "/gnd/{{id}}"));
		result.set("preview", Json.newObject()//
				.put("height", 100)//
				.put("width", 320)//
				.put("url", host + "/gnd/{{id}}.preview"));
		result.set("extend", Json.toJson(ImmutableMap.of(//
				"property_settings", Json.newArray()//
						.add(Json.newObject()//
								.put("name", "limit")//
								.put("label", "Limit")//
								.put("type", "number")//
								.put("default", 0)//
								.put("help_text", "Maximum number of values to return per row (0 for no limit)"))//
						.add(Json.newObject()//
								.put("name", "content")//
								.put("label", "Content")//
								.put("type", "select")//
								.put("default", "literal")//
								.put("help_text", "Content type: ID or literal")//
								.set("choices", Json.newArray().add(//
										Json.newObject()//
												.put("value", "id")//
												.put("name", "ID"))
										.add(Json.newObject()//
												.put("value", "literal")//
												.put("name", "Literal")))), //
				"propose_properties", Json.newObject()//
						.put("service_url", host)//
						.put("service_path", routes.Reconcile.properties("", "", "").toString()))));
		ObjectNode suggest = Json.newObject();
		suggest.set("property", suggestService("property"));
		suggest.set("entity", suggestService("entity"));
		suggest.set("type", suggestService("type"));
		result.set("suggest", suggest);
		return result;
	}

	private ObjectNode suggestService(String suggest) {
		return Json.newObject()//
				.put("service_url", HomeController.config("host") + "/gnd/reconcile")//
				.put("service_path", "/suggest/" + suggest)//
				.put("flyout_service_path", "/flyout/" + suggest + "?id=${id}");
	}

	/**
	 * @param callback
	 *            The name of the JSONP function to wrap the response in
	 * @param type
	 *            The type used in reconciliation
	 * @return Property proposal protocol data
	 */
	public Result properties(String callback, String type, String limit) {
		type = type.isEmpty() ? "AuthorityResource" : type;
		long L = limit.isEmpty() ? Long.MAX_VALUE : Long.parseLong(limit);
		List<Object> properties = GndOntology.properties(type).stream()
				.map(field -> ImmutableMap.of("id", field, "name", GndOntology.label(field))).limit(L)
				.collect(Collectors.toList());
		JsonNode response = (limit.isEmpty() ? Json.newObject() : Json.newObject().put("limit", L))//
				.put("type", type)//
				.set("properties", Json.toJson(properties));
		response().setHeader("Access-Control-Allow-Origin", "*");
		return callback.isEmpty() ? ok(response)
				: ok(String.format("/**/%s(%s);", callback, response.toString())).as("application/json");
	}

	private enum Service {
		ENTITY, TYPE, PROPERTY
	}

	/**
	 * Suggest API: suggest entry point
	 * 
	 * https://github.com/OpenRefine/OpenRefine/wiki/Suggest-API#suggest-entry-point
	 * 
	 * @param callback
	 *            The JSONP callback
	 * @param service
	 *            The service (one of: entity, type, property)
	 * @param prefix
	 *            The prefix to suggest something for
	 * @param type
	 *            The type or array-of-types of results to return
	 * @param typeStrict
	 *            How to deal with array-of-types (one of: any, all, should)
	 * @param limit
	 *            How many results to return
	 * @param start
	 *            First result to return
	 * @return The suggest JSON data
	 */
	public Result suggest(String callback, String service, String prefix, String type, String typeStrict, int limit,
			int start) {
		switch (Service.valueOf(service.toUpperCase())) {
		case ENTITY:
			Logger.info("Suggest {}:{} -> {}", service, prefix, Service.ENTITY);
			break;
		case TYPE:
			Logger.info("Suggest {}:{} -> {}", service, prefix, Service.TYPE);
			break;
		case PROPERTY:
			Logger.info("Suggest {}:{} -> {}", service, prefix, Service.PROPERTY);
			break;
		}
		return HomeController.withCallback(Json.toJson(Arrays.asList(ImmutableMap.of(//
				"id", "4042122-3", //
				"name", "Nichts", //
				"description", "Varianter Name: Nichtsein"))).toString());
	}

	/**
	 * Suggest API: flyout entry point
	 * 
	 * https://github.com/OpenRefine/OpenRefine/wiki/Suggest-API#flyout-entry-point
	 * 
	 * @param callback
	 *            The JSONP callback
	 * @param service
	 *            The service (one of: entity, type, property)
	 * @param id
	 *            The ID to return a flyout for
	 * @return The flyout JSON data
	 */
	public Result flyout(String callback, String service, String id) {
		switch (Service.valueOf(service.toUpperCase())) {
		case ENTITY:
			Logger.info("Flyout {}:{} -> {}", service, id, Service.ENTITY);
			break;
		case TYPE:
			Logger.info("Flyout {}:{} -> {}", service, id, Service.TYPE);
			break;
		case PROPERTY:
			Logger.info("Flyout {}:{} -> {}", service, id, Service.PROPERTY);
			break;
		}
		return HomeController.withCallback(Json
				.toJson(ImmutableMap.of(//
						"id", id, //
						"html", "<p style=\"font-size: 0.8em; color: black;\">Varianter Name: Nichtsein</p>"))
				.toString());
	}

	/** @return Reconciliation data for the queries in the request */
	public Result reconcile() {
		Map<String, String[]> body = request().body().asFormUrlEncoded();
		response().setHeader("Access-Control-Allow-Origin", "*");
		return body.containsKey("extend") ? ok(extend(body.get("extend")[0])) : ok(queries(body.get("queries")[0]));
	}

	private ObjectNode queries(String src) {
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
		return response;
	}

	private ObjectNode extend(String src) {
		JsonNode request = Json.parse(src);
		@SuppressWarnings("unchecked")
		List<HashMap<String, Object>> properties = Json.fromJson(request.get("properties"), List.class);
		List<Pair<String, Object>> propertyIdsAndSettings = properties.stream()
				.map((HashMap<String, Object> m) -> Pair.of(m.get("id").toString(), m.get("settings")))
				.collect(Collectors.toList());
		ObjectNode rows = Json.newObject();
		request.get("ids").elements().forEachRemaining(entityId -> {
			ObjectNode propertiesForId = Json.newObject();
			propertyIdsAndSettings.stream().forEach(property -> {
				propertiesForId.set(property.getLeft(),
						Json.toJson(propertyValues(entityId, property.getLeft(), property.getRight())));
			});
			rows.set(entityId.asText(), propertiesForId);
		});
		List<ObjectNode> meta = propertyIdsAndSettings.stream().map(property -> {
			ObjectNode m = Json.newObject()//
					.put("id", property.getLeft())//
					.put("name", GndOntology.label(property.getLeft()));
			String type = GndOntology.type(property.getLeft());
			if (type != null) {
				m.set("type", Json.newObject()//
						.put("id", type)//
						.put("name", GndOntology.label(type)));
			}
			return m;
		}).collect(Collectors.toList());
		return (ObjectNode) Json.toJson(ImmutableMap.of("meta", Json.toJson(meta), "rows", rows));
	}

	private List<Map<String, Object>> propertyValues(JsonNode entityId, String propertyId, Object setting) {
		Map<String, Object> entity = getAuthorityResource(entityId.asText());
		List<Map<String, Object>> result = new ArrayList<>();
		if (entity == null) {
			result.add(ImmutableMap.of());
		} else {
			JsonNode propertyJson = Json.toJson(entity.get(propertyId));
			ArrayNode values = propertyJson == null ? Json.newArray().add(Json.newObject())
					: (propertyJson.isArray() ? (ArrayNode) propertyJson : Json.newArray().add(propertyJson));
			asStream(values).limit(limit(setting)).forEach(node -> {
				if (node.isTextual()) {
					result.add(ImmutableMap.of("str", node.asText()));
				} else if (node.isObject()) {
					addValueForObject(result, node, content(setting));
				} else {
					result.add(ImmutableMap.of());
				}
			});
		}
		return result;
	}

	private Stream<JsonNode> asStream(ArrayNode values) {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(values.elements(), Spliterator.ORDERED), false);
	}

	private String content(Object setting) {
		JsonNode settingNode;
		return setting == null || (settingNode = Json.toJson(setting).get("content")) == null ? null
				: settingNode.asText();
	}

	private long limit(Object setting) {
		JsonNode limitNode;
		return setting == null || (limitNode = Json.toJson(setting).get("limit")) == null || limitNode.asLong() == 0
				? Long.MAX_VALUE
				: limitNode.asLong();
	}

	private void addValueForObject(List<Map<String, Object>> result, JsonNode node, String content) {
		if (!node.elements().hasNext()) {
			result.add(ImmutableMap.of());
			return;
		}
		String id = node.get("id").asText();
		String label = node.has("label") ? node.get("label").asText() : id;
		if (id.startsWith(AuthorityResource.DNB_PREFIX) && !id.endsWith("/about")) {
			/*
			 * See https://github.com/OpenRefine/OpenRefine/ wiki/
			 * Data-Extension-API#data-extension- protocol: "a reconciled value
			 * (from the same reconciliation service)"
			 */
			result.add(ImmutableMap.of("id", id.substring(AuthorityResource.DNB_PREFIX.length()), "name", label));
		} else {
			result.add(ImmutableMap.of("str", content != null && content.equals("id") ? id : label));
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
			List<JsonNode> types = StreamSupport.stream(Json.toJson(//
					hit.getSource().get("type")).spliterator(), false).collect(Collectors.toList());
			List<JsonNode> filtered = StreamSupport.stream(TYPES.spliterator(), false)
					.filter(t -> types.contains(Json.toJson(t.get("id")))).collect(Collectors.toList());
			resultForHit.set("type", Json.toJson(filtered));
			return resultForHit;
		}).collect(Collectors.toList());
	}

	private SearchResponse executeQuery(Entry<String, JsonNode> entry, String queryString) {
		JsonNode limitNode = entry.getValue().get("limit");
		int limit = limitNode == null ? -1 : limitNode.asInt();
		JsonNode typeNode = entry.getValue().get("type");
		String filter = typeNode == null ? "" : "type:" + typeNode.asText();
		return index.query(queryString, filter, "", 0, limit);
	}

	private String buildQueryString(Entry<String, JsonNode> entry) {
		String queryString = mainQuery(entry);
		JsonNode props = entry.getValue().get("properties");
		if (props != null) {
			for (JsonNode p : props) {
				queryString += " " + p.get("v").asText();
			}
		}
		return queryString.replaceAll("[:+\\-=<>(){}\\[\\]^]", "");
	}

	private String mainQuery(Entry<String, JsonNode> entry) {
		return entry.getValue().get("query").asText();
	}

}
