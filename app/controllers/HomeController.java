/* Copyright 2017-2018 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package controllers;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RiotNotFoundException;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;

import akka.stream.javadsl.Source;
import akka.util.ByteString;
import apps.Convert;
import controllers.Accept.Format;
import models.AuthorityResource;
import models.GndOntology;
import models.RdfConverter;
import models.RdfConverter.RdfFormat;
import modules.IndexComponent;
import play.Environment;
import play.Logger;
import play.libs.Json;
import play.libs.ws.WSBodyReadables;
import play.libs.ws.WSBodyWritables;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;
import play.mvc.Controller;
import play.mvc.Result;

/**
 * This controller contains an action to handle HTTP requests to the
 * application's home page.
 */
public class HomeController extends Controller implements WSBodyReadables, WSBodyWritables {

	private final WSClient httpClient;

	@Inject
	public HomeController(WSClient httpClient) {
		this.httpClient = httpClient;
	}

	public static final String[] AGGREGATIONS = new String[] { "type", "gndSubjectCategory.id", "geographicAreaCode.id",
			"professionOrOccupation.id", "dateOfBirth" };

	@Inject
	Environment env;

	@Inject
	IndexComponent index;

	public static final Config CONFIG = ConfigFactory.load();

	public static String config(String id) {
		return CONFIG.getString(id);
	}

	public static String configNested(String id, String sub) {
		ConfigObject object = CONFIG.getObject(id);
		return object.containsKey(sub) ? object.get(sub).unwrapped().toString() : null;
	}

	/**
	 * @param path
	 *            The path to redirect to
	 * @return A 301 MOVED_PERMANENTLY redirect to the path
	 */
	public Result redirectSlash(String path) {
		return movedPermanently("/" + path);
	}

	public Result index() {
		String queryString = "depiction:*";
		for (String dont : CONFIG.getStringList("dontShowOnMainPage")) {
			queryString += " AND NOT gndIdentifier:" + dont;
		}
		QueryStringQueryBuilder query = index.queryStringQuery(queryString);
		FunctionScoreQueryBuilder functionScoreQuery = QueryBuilders.functionScoreQuery(query,
				ScoreFunctionBuilders.randomFunction(System.currentTimeMillis()));
		SearchRequestBuilder requestBuilder = index.client().prepareSearch(config("index.prod.name"))
				.setQuery(functionScoreQuery).setFrom(0).setSize(1);
		SearchHits hits = requestBuilder.execute().actionGet().getHits();
		AuthorityResource entity = null;
		if (hits.getTotalHits() > 0) {
			SearchHit hit = hits.getAt(0);
			entity = entityWithImage(hit.getSourceAsString());
		}
		JsonNode dataset = Json.parse(readFile(config("dataset.file")));
		return ok(views.html.index.render(entity, dataset));
	}

	/**
	 * An action that renders an HTML page with a welcome message. The configuration
	 * in the <code>routes</code> file means that this method will be called when
	 * the application receives a <code>GET</code> request with a path of
	 * <code>/</code>.
	 */
	public Result api() {
		String format = "json";
		ImmutableMap<String, String> searchSamples = ImmutableMap.<String, String>builder()
				.put("Alles", controllers.routes.HomeController.search("*", "", "", 0, 10, format).toString())
				.put("Alle Felder",
						controllers.routes.HomeController.search("london", "", "", 0, 10, format).toString())
				.put("Feldsuche",
						controllers.routes.HomeController.search("preferredName:Twain", "", "", 0, 10, format)
								.toString())
				.put("Filter",
						controllers.routes.HomeController
								.search("preferredName:Twain", "type:Person", "", 0, 10, format).toString())
				.put("Paginierung",
						controllers.routes.HomeController.search("london", "", "", 50, 100, format).toString())
				.put("Sortierung",
						controllers.routes.HomeController
								.search("scholl", "", "preferredName.keyword:asc", 0, 10, format).toString())
				.put("ASCII", controllers.routes.HomeController
						.search("preferredName.ascii:Chor OR variantName.ascii:Chor", "", "", 0, 10, format).toString())
				.build();
		ImmutableMap<String, String> getSamples = ImmutableMap.of(//
				"London", controllers.routes.HomeController.authorityDotFormat("4074335-4", "json").toString(), //
				"hbz", controllers.routes.HomeController.authorityDotFormat("2047974-8", "json").toString(), //
				"Goethe", controllers.routes.HomeController.authorityDotFormat("118540238", "json").toString());
		return ok(views.html.api.render(searchSamples, getSamples));
	}

	/**
	 * @param id
	 *            The authority ID.
	 * @param format
	 *            The response format (see {@code Accept.Format})
	 * @return The details page for the authority with the given ID.
	 */
	public Result authorityDotFormat(final String id, String format) {
		return authority(id, format);
	}

	public Result authority(String id, String format) {
		SearchHits hits = index
				.query(String.format("deprecatedUri:\"%s%s\"", AuthorityResource.GND_PREFIX, id), "", "", 0, 1)
				.getHits();
		if (hits.getTotalHits() > 0 && !hits.getAt(0).getId().equals(id)) {
			return movedPermanently(controllers.routes.HomeController.authority(hits.getAt(0).getId(), format));
		}
		Format responseFormat = Accept.formatFor(format, request().acceptedTypes());
		if (responseFormat == null || responseFormat == Accept.Format.JSON_LINES
				|| format != null && format.contains(":")) {
			return unsupportedMediaType(views.html.error.render(id, String.format(
					"Unsupported for single resource: format=%s, accept=%s", format, request().acceptedTypes())));
		}
		String jsonLd = getAuthorityResource(id);
		if (jsonLd == null) {
			return responseFormat == Format.HTML ? notFound(views.html.details.render(null))
					: notFound("Not found: " + id);
		}
		try {
			switch (responseFormat) {
			case PREVIEW: {
				return ok(views.html.preview.render(toSuggestions(Json.parse("[" + jsonLd + "]"), "suggest")));
			}
			case HTML: {
				AuthorityResource entity = entityWithImage(jsonLd);
				entity.creatorOf = creatorOf(id);
				return ok(views.html.details.render(entity));
			}
			default: {
				JsonNode jsonLdObject = Json.parse(jsonLd);
				return rdfResultFor(jsonLdObject, responseFormat.queryParamString).orElseGet(() -> {
					return result(prettyJsonString(jsonLdObject), Accept.Format.JSON_LD.types[0]);
				});
			}
			}
		} catch (Exception e) {
			Logger.error("Could not create response", e);
			return internalServerError(views.html.error.render(id, e.getMessage()));
		}
	}

	private AuthorityResource entityWithImage(String jsonLd) {
		AuthorityResource entity = new AuthorityResource(Json.parse(jsonLd));
		if (entity.getImage().url.contains("File:"))
			entity.imageAttribution = attribution(
					entity.getImage().url.substring(entity.getImage().url.indexOf("File:") + 5).split("\\?")[0]);
		return entity;
	}

	private List<String> creatorOf(String id) {
		String q = String.format("firstAuthor:\"%s\" OR firstComposer:\"%s\"", id, id);
		SearchResponse response = index.query(q, "", "", 0, 1000);
		Stream<String> ids = Arrays.asList(response.getHits().getHits()).stream()
				.map(hit -> AuthorityResource.GND_PREFIX + hit.getId());
		return ids.collect(Collectors.toList());
	}

	private String getAuthorityResource(String id) {
		GetResponse response = index.client().prepareGet(config("index.prod.name"), config("index.type"), id).get();
		response().setHeader("Access-Control-Allow-Origin", "*");
		if (!response.isExists()) {
			return null;
		}
		return response.getSourceAsString();
	}

	private Optional<Result> rdfResultFor(JsonNode responseJson, String requestedFormat) {
		for (Format f : Format.values()) {
			RdfFormat rdfFormat;
			if (f.queryParamString.equals(requestedFormat) && (rdfFormat = RdfFormat.of(f.queryParamString)) != null) {
				String rdfContent = RdfConverter.toRdf(responseJson.toString(), rdfFormat);
				String contentType = f.types[0];
				return Optional.of(result(rdfContent, contentType));
			}
		}
		return Optional.empty();
	}

	private Result result(String content, String contentType) {
		return content.isEmpty() ? internalServerError("No content") : ok(content).as(contentType + "; charset=utf-8");
	}

	public Result context() {
		response().setHeader("Access-Control-Allow-Origin", "*");
		return ok(readFile(config("context.file"))).as(config("context.content"));
	}

	/**
	 * See https://www.w3.org/TR/dwbp/#metadata
	 * 
	 * @param format
	 *            The format ("jsonld" or "")
	 * 
	 * @return JSON-LD dataset metadata
	 */
	public Result dataset(String format) {
		response().setHeader("Access-Control-Allow-Origin", "*");
		Format responseFormat = Accept.formatFor(format, request().acceptedTypes());
		String content = readFile(config("dataset.file"));
		return responseFormat.queryParamString.startsWith("json") ? //
				ok(content).as(config("dataset.content")) : //
				ok(views.html.dataset.render(Json.parse(content)));
	}

	private String readFile(String name) {
		try {
			File file = env.getFile(name);
			Path path = Paths.get(file.getAbsolutePath());
			String content = Files.readAllLines(path).stream().collect(Collectors.joining("\n"));
			return content;
		} catch (IOException e) {
			Logger.error("Couldn't get: " + name, e);
			return null;
		}
	}

	public Result gnd(String id) {
		response().setHeader("Access-Control-Allow-Origin", "* ");
		Model sourceModel = ModelFactory.createDefaultModel();
		String sourceUrl = AuthorityResource.GND_PREFIX + id + "/about/lds";
		try {
			sourceModel.read(sourceUrl);
		} catch (HttpException e) {
			return status(e.getResponseCode(), e.getMessage());
		} catch (RiotNotFoundException e) {
			return status(404, e.getMessage());
		}
		String jsonLd = Convert.toJsonLd(id, sourceModel, env.isDev(), new HashSet<>());
		return ok(prettyJsonString(Json.parse(jsonLd))).as(config("index.content"));
	}

	public Result search(String q, String filter, String sort, int from, int size, String format) {
		Format responseFormat = Accept.formatFor(format, request().acceptedTypes());
		if (responseFormat == null || Stream.of(RdfFormat.values()).map(RdfFormat::getParam)
				.anyMatch(f -> f.equals(responseFormat.queryParamString))) {
			return unsupportedMediaType(views.html.error.render(q,
					String.format("Unsupported for search: format=%s, accept=%s", format, request().acceptedTypes())));
		}
		String queryString = (q == null || q.isEmpty()) ? "*" : q;
		try {
			SearchResponse response = index.query(queryString, filter, sort, from, size);
			response().setHeader("Access-Control-Allow-Origin", "*");
			String[] formatAndConfig = format == null ? new String[] {} : format.split(":");
			boolean returnSuggestions = formatAndConfig.length == 2;
			if (returnSuggestions) {
				List<Map<String, Object>> hits = Arrays.asList(response.getHits().getHits()).stream()
						.map(hit -> hit.getSource()).collect(Collectors.toList());
				return withCallback(toSuggestions(Json.toJson(hits), formatAndConfig[1]));
			}
			switch (responseFormat) {
			case HTML: {
				return htmlSearch(q, filter, from, size, responseFormat.queryParamString, response);
			}
			case JSON_LINES: {
				response().setHeader("Content-Disposition",
						String.format("attachment; filename=\"lobid-gnd-bulk-%s.jsonl\"", System.currentTimeMillis()));
				return jsonLines(queryString, filter, response);
			}
			default: {
				return ok(returnAsJson(q, response)).as(config("index.content"));
			}
			}
		} catch (Throwable t) {
			String message = t.getMessage() + (t.getCause() != null ? ", cause: " + t.getCause().getMessage() : "");
			Logger.error("Error: " + message, t);
			return internalServerError(views.html.error.render(q, "Error: " + message));
		}
	}

	private Result jsonLines(String q, String filter, SearchResponse response) {
		BoolQueryBuilder query = QueryBuilders.boolQuery().must(index.queryStringQuery(q));
		if (!filter.isEmpty()) {
			query = query.filter(index.queryStringQuery(filter));
		}
		TimeValue keepAlive = new TimeValue(60000);
		SearchRequestBuilder scrollRequest = index.client().prepareSearch(config("index.prod.name"))
				.addSort(FieldSortBuilder.DOC_FIELD_NAME, SortOrder.ASC).setScroll(keepAlive).setQuery(query)
				.setSize(100 /* hits per shard for each scroll */);
		Logger.debug("Scrolling with query: q={}, request={}", q, scrollRequest);
		Source<ByteString, ?> source = Source.from(() -> hitIterator(scrollRequest.get(), keepAlive));
		return ok().chunked(source).as(Accept.Format.JSON_LINES.types[0]);
	}

	private Iterator<ByteString> hitIterator(SearchResponse scrollResponse, TimeValue keepAlive) {
		return new Iterator<ByteString>() {
			Iterator<SearchHit> iterator = scrollResponse.getHits().iterator();

			@Override
			public boolean hasNext() {
				if (!iterator.hasNext()) {
					iterator = index.client().prepareSearchScroll(scrollResponse.getScrollId())//
							.setScroll(keepAlive).execute().actionGet().getHits().iterator();
				}
				return iterator.hasNext();
			}

			@Override
			public ByteString next() {
				return ByteString.fromString(iterator.next().getSourceAsString() + "\n");
			}
		};
	}

	private Result htmlSearch(String q, String type, int from, int size, String format, SearchResponse response) {
		return ok(views.html.search.render(q, type, from, size, returnAsJson(q, response),
				response == null ? 0 : response.getHits().getTotalHits()));
	}

	static Result withCallback(final String json) {
		/* JSONP callback support for remote server calls with JavaScript: */
		final String[] callback = request() == null || request().queryString() == null ? null
				: request().queryString().get("callback");
		return callback != null
				? ok(String.format("/**/%s(%s)", callback[0], json)).as("application/javascript; charset=utf-8")
				: ok(json).as("application/json; charset=utf-8");
	}

	static String toSuggestions(JsonNode json, String labelFields) {
		Stream<String> defaultFields = Stream.of("preferredName", "dateOfBirth-dateOfDeath", "professionOrOccupation",
				"placeOfBusiness", "firstAuthor", "firstComposer", "dateOfProduction");
		String fields = labelFields.equals("suggest") ? defaultFields.collect(Collectors.joining(",")) : labelFields;
		Stream<JsonNode> documents = Lists.newArrayList(json.elements()).stream();
		Stream<Map<String, Object>> suggestions = documents.map((JsonNode document) -> {
			Optional<JsonNode> id = getOptional(document, "id");
			Optional<JsonNode> type = getOptional(document, "type");
			Stream<String> labels = Arrays.asList(fields.split(",")).stream().map(String::trim)
					.map(field -> AuthorityResource.fieldValues(field, document)
							.collect(Collectors.joining(", ")));
			List<String> categories = filtered(Lists.newArrayList(type.orElseGet(() -> Json.toJson("[]")).elements())
					.stream().map(JsonNode::asText).filter(t -> !t.equals("AuthorityResource"))
					.collect(Collectors.toList()));
			return toSuggestionsMap(document, id, labels, categories);
		});
		return Json.toJson(suggestions.distinct().collect(Collectors.toList())).toString();
	}

	@SuppressWarnings("serial")
	private static Map<String, Object> toSuggestionsMap(JsonNode document, Optional<JsonNode> id, Stream<String> labels,
			List<String> categories) {
		return new HashMap<String, Object>() {
			{
				put("label", labels.filter(t -> !t.trim().isEmpty()).collect(Collectors.joining(" | ")));
				put("id", id.orElseGet(() -> Json.toJson("")));
				put("category",
						categories.stream().map(t -> GndOntology.label(t)).sorted().collect(Collectors.joining(" | ")));
				String image = new AuthorityResource(document).getImage().image;
				if (!image.trim().isEmpty()) {
					put("image", image.replaceAll("width=\\d+", "width=100"));
				}
			}
		};
	}

	private static List<String> filtered(List<String> allTypes) {
		List<String> subTypes = allTypes.stream()
				.filter(t -> HomeController.CONFIG.getObject("types").keySet().contains(t))
				.collect(Collectors.toList());
		return (subTypes.size() == 0 ? allTypes : subTypes);
	}

	private static Optional<JsonNode> getOptional(JsonNode json, String field) {
		return Optional.ofNullable(json.get(field));
	}

	/**
	 * @return The current full URI, URL-encoded, or null.
	 */
	public static String currentUri() {
		try {
			return URLEncoder.encode(request().host() + request().uri(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			Logger.error("Could not get current URI", e);
		}
		return null;
	}

	static String returnAsJson(String q, SearchResponse queryResponse) {
		if (queryResponse == null) {
			return Json.newObject().toString();
		}
		List<Map<String, Object>> hits = Arrays.asList(queryResponse.getHits().getHits()).stream()
				.map(hit -> hit.getSource()).collect(Collectors.toList());
		ObjectNode object = Json.newObject();
		object.put("@context", config("host") + routes.HomeController.context());
		object.put("id", config("host") + request().uri());
		object.put("totalItems", queryResponse.getHits().getTotalHits());
		object.set("member", Json.toJson(hits));

		Map<String, Object> map = new HashMap<>();
		for (String a : AGGREGATIONS) {
			Aggregation aggregation = queryResponse.getAggregations().get(a);
			Terms terms = (Terms) aggregation;
			Stream<? extends Bucket> stream = terms.getBuckets().stream()
					.filter(b -> !b.getKeyAsString().equals("AuthorityResource"));
			Stream<Map<String, Object>> buckets = stream.map((Bucket b) -> ImmutableMap.of(//
					"key", b.getKeyAsString(), "doc_count", b.getDocCount()));
			map.put(a, Json.toJson(buckets.collect(Collectors.toList())));
		}
		object.set("aggregation", Json.toJson(map));
		return prettyJsonString(object);
	}

	static String prettyJsonString(JsonNode jsonNode) {
		try {
			return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
		} catch (JsonProcessingException x) {
			x.printStackTrace();
			return null;
		}
	}

	public static String formatCount(long count) {
		DecimalFormat df = (DecimalFormat) (DecimalFormat.getInstance(Locale.GERMAN));
		DecimalFormatSymbols symbols = df.getDecimalFormatSymbols();
		symbols.setGroupingSeparator('.');
		df.setDecimalFormatSymbols(symbols);
		return df.format(count);
	}

	private String attribution(String url) {
		try {
			return requestInfo(httpClient, url).thenApply(info -> {
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

}
