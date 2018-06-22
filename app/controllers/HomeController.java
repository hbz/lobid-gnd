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
		return ok(views.html.index.render());
	}

	/**
	 * An action that renders an HTML page with a welcome message. The configuration
	 * in the <code>routes</code> file means that this method will be called when
	 * the application receives a <code>GET</code> request with a path of
	 * <code>/</code>.
	 */
	public Result api() {
		String format = "json";
		ImmutableMap<String, String> searchSamples = ImmutableMap.of(//
				"Alles", controllers.routes.HomeController.search("*", "", 0, 10, format).toString(), //
				"Alle Felder", controllers.routes.HomeController.search("london", "", 0, 10, format).toString(), //
				"Feldsuche",
				controllers.routes.HomeController.search("preferredName:Twain", "", 0, 10, format).toString(), //
				"Filter",
				controllers.routes.HomeController.search("preferredName:Twain", "type:Person", 0, 10, format)
						.toString(), //
				"Paginierung", controllers.routes.HomeController.search("london", "", 50, 100, format).toString());
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
				.query(String.format("deprecatedUri:\"%s%s\"", AuthorityResource.DNB_PREFIX, id), "", 0, 1).getHits();
		if (hits.getTotalHits() > 0 && !hits.getAt(0).getId().equals(id)) {
			return movedPermanently(controllers.routes.HomeController.authority(hits.getAt(0).getId(), format));
		}
		String jsonLd = getAuthorityResource(id);
		if (jsonLd == null) {
			return notFound("Not found: " + id);
		}
		Format responseFormat = Accept.formatFor(format, request().acceptedTypes());
		try {
			switch (responseFormat) {
			case HTML: {
				AuthorityResource entity = new AuthorityResource(Json.parse(jsonLd));
				if (entity.getImage().url.contains("File:"))
					entity.imageAttribution = attribution(entity.getImage().url
							.substring(entity.getImage().url.indexOf("File:") + 5).split("\\?")[0]);
				entity.creatorOf = creatorOf(id);
				return ok(views.html.details.render(entity));
			}
			default: {
				return rdfResultFor(Json.parse(jsonLd), responseFormat.queryParamString).orElseGet(() -> {
					return result(jsonLd, Accept.Format.JSON_LD.types[0]);
				});
			}
			}
		} catch (Exception e) {
			Logger.error("Could not create response", e);
			return internalServerError(e.getMessage());
		}
	}

	private List<String> creatorOf(String id) {
		String q = String.format("firstAuthor:\"%s\" OR firstComposer:\"%s\"", id, id);
		SearchResponse response = index.query(q, "", 0, 1000);
		Stream<String> ids = Arrays.asList(response.getHits().getHits()).stream()
				.map(hit -> AuthorityResource.DNB_PREFIX + hit.getId());
		return ids.collect(Collectors.toList());
	}

	private String getAuthorityResource(String id) {
		GetResponse response = index.client().prepareGet(config("index.name"), config("index.type"), id).get();
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
		try {
			File file = env.getFile(config("context.file"));
			Path path = Paths.get(file.getAbsolutePath());
			return ok(Files.readAllLines(path).stream().collect(Collectors.joining("\n")))
					.as(config("context.content"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public Result gnd(String id) {
		response().setHeader("Access-Control-Allow-Origin", "* ");
		Model sourceModel = ModelFactory.createDefaultModel();
		String sourceUrl = "http://d-nb.info/gnd/" + id + "/about/lds";
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

	public Result search(String q, String filter, int from, int size, String format) {
		Format responseFormat = Accept.formatFor(format, request().acceptedTypes());
		String queryString = (q == null || q.isEmpty()) ? "*" : q;
		SearchResponse response = index.query(queryString, filter, from, size);
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
	}

	private Result jsonLines(String q, String filter, SearchResponse response) {
		BoolQueryBuilder query = QueryBuilders.boolQuery().must(QueryBuilders.queryStringQuery(q));
		if (!filter.isEmpty()) {
			query = query.filter(QueryBuilders.queryStringQuery(filter));
		}
		TimeValue keepAlive = new TimeValue(60000);
		SearchRequestBuilder scrollRequest = index.client().prepareSearch(config("index.name"))
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
				response.getHits().getTotalHits()));
	}

	private static Result withCallback(final String json) {
		/* JSONP callback support for remote server calls with JavaScript: */
		final String[] callback = request() == null || request().queryString() == null ? null
				: request().queryString().get("callback");
		return callback != null
				? ok(String.format("/**/%s(%s)", callback[0], json)).as("application/javascript; charset=utf-8")
				: ok(json).as("application/json; charset=utf-8");
	}

	private static String toSuggestions(JsonNode json, String labelFields) {
		Stream<String> defaultFields = Stream.of("preferredName", "dateOfBirth-dateOfDeath", "professionOrOccupation",
				"placeOfBusiness", "firstAuthor", "firstComposer", "dateOfProduction");
		String fields = labelFields.equals("suggest") ? defaultFields.collect(Collectors.joining(",")) : labelFields;
		Stream<JsonNode> documents = Lists.newArrayList(json.elements()).stream();
		Stream<JsonNode> suggestions = documents.map((JsonNode document) -> {
			Optional<JsonNode> id = getOptional(document, "id");
			Optional<JsonNode> type = getOptional(document, "type");
			Stream<String> labels = Arrays.asList(fields.split(",")).stream().map(String::trim)
					.map(field -> AuthorityResource.fieldValues(field, document).map((JsonNode node) -> //
			(node.isTextual() ? Optional.ofNullable(node) : Optional.ofNullable(node.findValue("label")))
					.orElseGet(() -> Json.toJson("")).asText()).collect(Collectors.joining(", ")));
			List<String> categories = filtered(Lists.newArrayList(type.orElseGet(() -> Json.toJson("[]")).elements())
					.stream().map(JsonNode::asText).filter(t -> !t.equals("AuthorityResource"))
					.collect(Collectors.toList()));
			return Json.toJson(ImmutableMap.of(//
					"label", labels.filter(t -> !t.trim().isEmpty()).collect(Collectors.joining(" | ")), //
					"id", id.orElseGet(() -> Json.toJson("")), //
					"category",
					categories.stream().map(t -> GndOntology.label(t)).sorted().collect(Collectors.joining(" | "))));
		});
		return Json.toJson(suggestions.distinct().collect(Collectors.toList())).toString();
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

	private static String returnAsJson(String q, SearchResponse queryResponse) {
		List<Map<String, Object>> hits = Arrays.asList(queryResponse.getHits().getHits()).stream()
				.map(hit -> hit.getSource()).collect(Collectors.toList());
		ObjectNode object = Json.newObject();
		object.put("@context", "http://" + request().host() + routes.HomeController.context());
		object.put("id", "http://" + request().host() + request().uri());
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

	private static String prettyJsonString(JsonNode jsonNode) {
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
