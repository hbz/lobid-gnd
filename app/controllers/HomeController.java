package controllers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import apps.Convert;
import modules.IndexComponent;
import play.Environment;
import play.Logger;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

/**
 * This controller contains an action to handle HTTP requests to the
 * application's home page.
 */
public class HomeController extends Controller {

	@Inject
	Environment env;

	@Inject
	IndexComponent index;

	private static final Config CONFIG = ConfigFactory.load();

	public static String config(String id) {
		return CONFIG.getString(id);
	}

	/**
	 * An action that renders an HTML page with a welcome message. The
	 * configuration in the <code>routes</code> file means that this method will
	 * be called when the application receives a <code>GET</code> request with a
	 * path of <code>/</code>.
	 */
	public Result index() {
		ImmutableMap<String, String> searchSamples = ImmutableMap.of(//
				"All", controllers.routes.HomeController.search("*").toString(), //
				"All fields", controllers.routes.HomeController.search("london").toString(), //
				"Field search", controllers.routes.HomeController.search("type:CorporateBody").toString());
		ImmutableMap<String, String> getSamples = ImmutableMap.of(//
				"London", controllers.routes.HomeController.authority("4074335-4").toString(), //
				"hbz", controllers.routes.HomeController.authority("2047974-8").toString(), //
				"Goethe", controllers.routes.HomeController.authority("118540238").toString());
		return ok(views.html.index.render(searchSamples, getSamples));
	}

	public Result authority(String id) {
		GetResponse response = index.client().prepareGet(config("index.name"), config("index.type"), id).get();
		response().setHeader("Access-Control-Allow-Origin", "*");
		if (!response.isExists()) {
			Logger.warn("{} does not exists in index, falling back to live version from GND", id);
			return gnd(id);
		}
		String jsonLd = response.getSourceAsString();
		return ok(jsonLd).as(config("index.content"));
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

	// TODO add route or parameter for testing live version from GND
	public Result gnd(String id) {
		response().setHeader("Access-Control-Allow-Origin", "* ");
		Model sourceModel = ModelFactory.createDefaultModel();
		String sourceUrl = "http://d-nb.info/gnd/" + id + "/about/lds";
		sourceModel.read(sourceUrl);
		String jsonLd = Convert.toJsonLd(id, sourceModel, env.isDev());
		return ok(jsonLd).as(config("index.content"));
	}

	public Result search(String q) {
		SearchResponse response = index.client().prepareSearch(config("index.name"))
				.setQuery(QueryBuilders.queryStringQuery(q)).get();
		response().setHeader("Access-Control-Allow-Origin", "*");
		return ok(returnAsJson(response)).as(config("index.content"));
	}

	private static String returnAsJson(SearchResponse queryResponse) {
		List<Map<String, Object>> hits = Arrays.asList(queryResponse.getHits().hits()).stream()
				.map(hit -> hit.getSource()).collect(Collectors.toList());
		ObjectNode object = Json.newObject();
		object.put("@context", "http://" + request().host() + routes.HomeController.context());
		object.put("id", "http://" + request().host() + request().uri());
		object.put("totalItems", queryResponse.getHits().getTotalHits());
		object.set("member", Json.toJson(hits));
		JsonNode aggregations = Json.parse(queryResponse.toString()).get("aggregations");
		if (aggregations != null) {
			object.set("aggregation", aggregations);
		}
		return prettyJsonOk(object);
	}

	private static String prettyJsonOk(JsonNode jsonNode) {
		try {
			return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
		} catch (JsonProcessingException x) {
			x.printStackTrace();
			return null;
		}
	}
}
