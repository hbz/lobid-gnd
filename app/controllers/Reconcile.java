/* Copyright 2017-2022 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package controllers;

import static controllers.HomeController.config;
import static controllers.HomeController.prettyJsonString;
import static controllers.HomeController.withCallback;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import org.elasticsearch.index.query.QueryStringQueryBuilder;
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
				ImmutableMap.Builder<Object, Object> map = ImmutableMap.builder()//
						.put("id", type)//
						.put("name", GndOntology.label(type));
				return type.equals(AuthorityResource.ID) ? map.build()
						: map.put("broader",
								Arrays.asList(ImmutableMap.of(//
										"id", AuthorityResource.ID, //
										"name", GndOntology.label(AuthorityResource.ID))))
								.build();
			}));

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z")
			.withZone(ZoneId.systemDefault());

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
		if (Accept.formatFor(null, request().acceptedTypes()) == Accept.Format.HTML
				&& queries.isEmpty() && extend.isEmpty()) {
			return ok(views.html.reconcile.render());
		} else {
			ObjectNode result = queries.isEmpty() && extend.isEmpty() ? metadata()
					: (!queries.isEmpty() ? queries(queries) : extend(extend));
			response().setHeader("Access-Control-Allow-Origin", "*");
			final String resultString = prettyJsonString(result);
			return (callback.isEmpty() ? ok(resultString)
					: ok(String.format("/**/%s(%s);", callback, resultString))).as("application/json; charset=utf-8");
		}
	}

	private ObjectNode metadata() {
		final String host = HomeController.configNested("host", "reconcile");
		ObjectNode result = Json.newObject();
		result.putArray("versions").add("0.1").add("0.2");
		result.put("name", "GND reconciliation for OpenRefine");
		result.put("identifierSpace", AuthorityResource.GND_PREFIX);
		result.put("schemaSpace", "https://d-nb.info/standards/elementset/gnd#AuthorityResource");
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
				.put("service_url", HomeController.configNested("host", "reconcile"))//
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
		final String responseString = prettyJsonString(response);
		return (callback.isEmpty() ? ok(responseString)
				: ok(String.format("/**/%s(%s);", callback, responseString))).as("application/json; charset=utf-8");
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
		response().setHeader("Access-Control-Allow-Origin", "*");
		switch (Service.valueOf(service.toUpperCase())) {
		case ENTITY:
			Logger.debug("Suggest {}:{} -> {}", service, prefix, Service.ENTITY);
			List<?> results = StreamSupport
					.stream(index.query(preprocess(prefix), type, "", start, limit).getHits().spliterator(), false)
					.map(hit -> new AuthorityResource(Json.parse(hit.getSourceAsString())))
					.map(entity -> Json.toJson(ImmutableMap.of(//
							"id", entity.getId(), //
							"name", entity.title(), //
							"description", entity.subTitle(), //
							"notable",
							entity.getType().stream()
									.map(t -> Json.toJson(ImmutableMap.of(//
											"id", t, //
											"name", GndOntology.label(t)))))))
					.collect(Collectors.toList());
			return withCallback(prettyJsonString(suggestApiResponse(prefix, results)));
		case TYPE:
			Logger.debug("Suggest {}:{} -> {}", service, prefix, Service.TYPE);
			SearchResponse aggregationQuery = index.query("*", "", "", start, limit);
			Stream<JsonNode> labelledTypes = labelledIds(
					StreamSupport.stream(Json.parse(HomeController.returnAsJson("*", aggregationQuery))
							.get("aggregation").get("type").spliterator(), false).map(t -> t.get("key").asText()));
			return withCallback(prettyJsonString(suggestApiResponse(prefix, matchingEntries(prefix, labelledTypes))));
		case PROPERTY:
			Logger.debug("Suggest service: {} prefix: {} type: {} -> {}", service, prefix, type, Service.PROPERTY);
			Stream<String> propertyIds = GndOntology.properties(type.contains("/") ? "" : type).stream();
			Stream<JsonNode> labelledProperties = labelledIds(propertyIds);
			return withCallback(prettyJsonString(suggestApiResponse(prefix, matchingEntries(prefix, labelledProperties))));
		}
		return withCallback(prettyJsonString(suggestApiResponse(prefix,
				Arrays.asList(ImmutableMap.of(//
						"id", "4042122-3", //
						"name", "Nichts", //
						"description", "Varianter Name: Nichtsein")))));
	}

	private JsonNode suggestApiResponse(String prefix, List<?> results) {
		return Json.toJson(ImmutableMap.of(//
				"code", "/api/status/ok", //
				"status", "200 OK", //
				"prefix", prefix, //
				"result", results));
	}

	private Stream<JsonNode> labelledIds(Stream<String> ids) {
		Stream<JsonNode> labelledIds = ids.map(id -> {
			ImmutableMap.Builder<Object, Object> map = ImmutableMap.builder()//
					.put("id", id)//
					.put("name", GndOntology.label(id));
			String broaderId = HomeController.configNested("types", id);
			return Json.toJson(broaderId == null ? map.build()
					: map.put("broader",
							Arrays.asList(ImmutableMap.of(//
									"id", broaderId, //
									"name", GndOntology.label(broaderId))))
							.build());
		});
		return labelledIds;
	}

	private List<JsonNode> matchingEntries(String prefix, Stream<JsonNode> labelledIds) {
		return labelledIds//
				.filter(candidate -> candidate.toString().toLowerCase().contains(prefix.toLowerCase()))
				.collect(Collectors.toList());
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
			Logger.debug("Flyout {}:{} -> {}", service, id, Service.ENTITY);
			return HomeController.withCallback(prettyJsonString(Json.toJson(ImmutableMap.of(//
					"id", id, //
					"html", previewHtml(id)))));
		case TYPE:
			Logger.debug("Flyout {}:{} -> {}", service, id, Service.TYPE);
			break;
		case PROPERTY:
			Logger.debug("Flyout {}:{} -> {}", service, id, Service.PROPERTY);
			break;
		}
		return HomeController.withCallback(prettyJsonString(Json.toJson(ImmutableMap.of(//
				"id", id, //
				"html", labelAndIdHtml(id)))));
	}

	private String previewHtml(String id) {
		GetResponse getResponse = index.client().prepareGet().setIndex(config("index.prod.name")).setId(id).get();
		JsonNode entityJson = Json.parse(getResponse.getSourceAsString());
		return views.html.preview.render(//
				HomeController.toSuggestions(Json.parse("[" + entityJson + "]"), "suggest")).toString();
	}

	private String labelAndIdHtml(String id) {
		return "<p style=\"font-size: 0.8em; color: black;\"><b>" + GndOntology.label(id) + "</b> (" + id + ")</p>";
	}

	/** @return Reconciliation data for the queries in the request */
	public Result reconcile() {
		Map<String, String[]> body = request().body().asFormUrlEncoded();
		response().setHeader("Access-Control-Allow-Origin", "*");
		Result result = body.containsKey("extend") ? ok(extend(body.get("extend")[0])) : ok(queries(body.get("queries")[0]));
		// Apache-compatible POST logging, see https://github.com/hbz/lobid-gnd/issues/207#issuecomment-526571646
		Logger.info("{} {} - [{}] \"{} {}\" {}", request().header("X-Forwarded-For").orElse(request().remoteAddress()),
				request().host(), TIME_FORMATTER.format(Instant.now()), request().method(), request().path(),
				result.status());
		return result;
	}

	private ObjectNode queries(String src) {
		JsonNode request = Json.parse(src);
		Iterator<Entry<String, JsonNode>> inputQueries = request.fields();
		ObjectNode response = Json.newObject();
		while (inputQueries.hasNext()) {
			Entry<String, JsonNode> inputQuery = inputQueries.next();
			Logger.debug("q: " + inputQuery);
			SearchResponse searchResponse = executeQuery(inputQuery, preprocess(mainQuery(inputQuery)),
					propQuery(inputQuery));
			List<ObjectNode> results = mapToResults(mainQuery(inputQuery), searchResponse.getHits());
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
		if (id.startsWith(AuthorityResource.GND_PREFIX) && !id.endsWith("/about")) {
			/*
			 * See https://github.com/OpenRefine/OpenRefine/ wiki/
			 * Data-Extension-API#data-extension- protocol: "a reconciled value
			 * (from the same reconciliation service)"
			 */
			result.add(ImmutableMap.of("id", id.substring(AuthorityResource.GND_PREFIX.length()), "name", label));
		} else {
			result.add(ImmutableMap.of("str", content != null && content.equals("id") ? id : label));
		}
	}

	private Map<String, Object> getAuthorityResource(String id) {
		GetResponse response = index.client()
				.prepareGet(HomeController.config("index.prod.name"), HomeController.config("index.type"), id).get();
		if (!response.isExists()) {
			return null;
		}
		return response.getSource();
	}

	private List<ObjectNode> mapToResults(String mainQuery, SearchHits searchHits) {
		List<ObjectNode> result = Arrays.asList(searchHits.getHits()).stream().map(hit -> {
			Map<String, Object> map = hit.getSource();
			ObjectNode resultForHit = Json.newObject();
			resultForHit.put("id", hit.getId());
			Object nameObject = map.get("preferredName");
			String name = nameObject == null ? "" : nameObject + "";
			resultForHit.put("name", name);
			resultForHit.put("score", hit.getScore());
			resultForHit.put("match", false);
			List<JsonNode> types = StreamSupport.stream(Json.toJson(//
					hit.getSource().get("type")).spliterator(), false).collect(Collectors.toList());
			List<JsonNode> filtered = StreamSupport.stream(TYPES.spliterator(), false)
					.filter(t -> types.contains(Json.toJson(t.get("id")))).collect(Collectors.toList());
			resultForHit.set("type", Json.toJson(filtered));
			return resultForHit;
		}).collect(Collectors.toList());
		markMatch(result);
		return result;
	}

	private void markMatch(List<ObjectNode> result) {
		if (!result.isEmpty()) {
			ObjectNode topResult = result.get(0);
			int bestScore = topResult.get("score").asInt();
			if (bestScore > 50 && (result.size() == 1 || bestScore - result.get(1).get("score").asInt() >= 5)) {
				topResult.put("match", true);
			}
		}
	}

	private SearchResponse executeQuery(Entry<String, JsonNode> entry, String queryString, String propString) {
		JsonNode limitNode = entry.getValue().get("limit");
		int limit = limitNode == null ? -1 : limitNode.asInt();
		JsonNode typeNode = entry.getValue().get("type");
		String filter = typeNode == null ? "" : "type:" + typeNode.asText();
		QueryStringQueryBuilder mainQuery = index.queryStringQuery(queryString)//
				.field("preferredName", 4f)//
				.field("variantName", 2f)//
				.field("temporaryName")//
				.field("temporaryNameOfTheConferenceOrEvent")//
				.field("temporaryNameOfTheCorporateBody")//
				.field("temporaryNameOfThePlaceOrGeographicName")//
				.field("abbreviatedName")//
				.field("abbreviatedNameForTheConferenceOrEvent")//
				.field("abbreviatedNameForThePlaceOrGeographicName")//
				.field("abbreviatedNameForTheWork")//
				.field("abbreviatedNameForTheCorporateBody")//
				.field("gndIdentifier")//
				.field("sameAs.id")//
				.field("id");//
		QueryStringQueryBuilder propQuery = index.queryStringQuery(propString).boost(5f);
		return index.query(mainQuery, filter, propQuery, "", 0, limit);
	}

	private String propQuery(Entry<String, JsonNode> entry) {
		List<String> segments = new ArrayList<>();
		JsonNode props = entry.getValue().get("properties");
		if (props != null) {
			Logger.debug("Properties: {}", props);
			for (JsonNode p : props) {
				String field = p.get("pid").asText();
				String value = p.get("v").asText().trim();
				if (!value.isEmpty()) {
					// if pid is a valid field, add field search, else just value:
					String segment = value;
					if (GndOntology.properties("").contains(field)) {
						if (field.endsWith("AsLiteral") || Arrays.asList("type", "dateOfBirth", "dateOfDeath",
								"oldAuthorityNumber", "biographicalOrHistoricalInformation", "gndIdentifier",
								"preferredName", "variantName").contains(field)) {
							segment = String.format("%s:%s", field, preprocess(value));
						} else if (value.startsWith("http")) {
							segment = String.format("%s.\\*:\"%s\"", field, value);
						} else {
							segment = String.format("%s.\\*:%s", field, preprocess(value));
						}
					}
					segments.add("(" + segment + ")");
				}
			}
		}
		String queryString = segments.stream().collect(Collectors.joining(" OR "));
		Logger.debug("Property query string: {}", queryString);
		return queryString;
	}

	String preprocess(String s) {
		return s.startsWith("http") || isGndId(s) ? "\"" + s + "\"" : index.validate(s) ? s : clean(s);
	}

	private static boolean isGndId(String string) {
		return string.matches(
				// https://www.wikidata.org/wiki/Property:P227#P1793
				"1[012]?\\d{7}[0-9X]|[47]\\d{6}-\\d|[1-9]\\d{0,7}-[0-9X]|3\\d{7}[0-9X]");
	}

	private static String clean(String in) {
		String out = in.replaceAll("[-:+=&|><!(){}\\[\\]\"~*?\\\\/\\^]", " ");
		Logger.info("Cleaned invalid query string '{}' to: '{}'", in, out);
		return out;
	}

	private String mainQuery(Entry<String, JsonNode> entry) {
		return entry.getValue().get("query").asText();
	}
}
