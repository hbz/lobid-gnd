package controllers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.jena.JenaRDFParser;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.ImmutableMap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

import play.Environment;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

/**
 * This controller contains an action to handle HTTP requests to the
 * application's home page.
 */
public class HomeController extends Controller {

	private static final String AUTH_RESOURCE = //
			"http://d-nb.info/standards/elementset/gnd#AuthorityResource";

	@Inject
	Environment env;

	/**
	 * An action that renders an HTML page with a welcome message. The
	 * configuration in the <code>routes</code> file means that this method will
	 * be called when the application receives a <code>GET</code> request with a
	 * path of <code>/</code>.
	 */
	public Result index() {
		return ok(views.html.index.render());
	}

	public Result authority(String name) {
		response().setHeader("Access-Control-Allow-Origin", "*");
		return ok(asJsonLd(name)).as("application/json; charset=utf-8");
	}

	public Result context() {
		response().setHeader("Access-Control-Allow-Origin", "*");
		try {
			File file = env.getFile("conf/context.jsonld");
			Path path = Paths.get(file.getAbsolutePath());
			return ok(Files.readAllLines(path).stream().collect(Collectors.joining("\n")))
					.as("application/ld+json; charset=utf-8");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private String asJsonLd(String gndId) {
		String contextUrl = env.isDev() ? "http://localhost:9000/authorities/context.jsonld"
				: "https://raw.githubusercontent.com/hbz/lobid-authorities/master/conf/context.jsonld";
		ImmutableMap<String, String> frame = ImmutableMap.of("@context", contextUrl, "@type", AUTH_RESOURCE);
		JsonLdOptions options = new JsonLdOptions();
		options.setCompactArrays(false);
		try {
			Object jsonLd = JsonLdProcessor.fromRDF(sourceModel(gndId), new JenaRDFParser());
			jsonLd = preprocess(jsonLd, gndId);
			jsonLd = JsonLdProcessor.frame(jsonLd, new HashMap<>(frame), options);
			jsonLd = JsonLdProcessor.compact(jsonLd, contextUrl, options);
			return postprocess(contextUrl, jsonLd);
		} catch (JsonLdError e) {
			e.printStackTrace();
			return null;
		}
	}

	private Model sourceModel(String gndId) {
		Model sourceModel = ModelFactory.createDefaultModel();
		String sourceUrl = "http://d-nb.info/gnd/" + gndId + "/about/lds";
		sourceModel.read(sourceUrl);
		return sourceModel;
	}

	private Object preprocess(Object jsonLd, String gndId) {
		List<JsonNode> processed = withSuperclass(Json.toJson(jsonLd), gndId);
		try {
			return JsonUtils.fromString(Json.toJson(processed).toString());
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private String postprocess(String contextUrl, Object jsonLd) {
		JsonNode first = Json.toJson(jsonLd).get("@graph").elements().next();
		if (first.isObject()) {
			@SuppressWarnings("unchecked") /* first.isObject() */
			Map<String, Object> res = Json.fromJson(first, TreeMap.class);
			res.put("@context", contextUrl);
			return Json.prettyPrint(Json.toJson(res));
		}
		return Json.prettyPrint(Json.toJson(jsonLd));
	}

	private List<JsonNode> withSuperclass(JsonNode json, String id) {
		Stream<JsonNode> stream = asStream(json.elements()).map(e -> {
			if (e.get("@id").toString().contains(id) && e.isObject()) {
				@SuppressWarnings("unchecked") /* e.isObject() */
				Map<String, Object> doc = Json.fromJson(e, Map.class);
				doc.put("@type", addType(doc, AUTH_RESOURCE));
				return Json.toJson(doc);
			} else {
				return e;
			}
		});
		return stream.collect(Collectors.toList());
	}

	private Object addType(Map<String, Object> doc, String newType) {
		JsonNode json = Json.toJson(doc.get("@type"));
		if (json.isArray()) {
			@SuppressWarnings("unchecked") /* json.isArray() */
			List<String> types = Json.fromJson(json, List.class);
			types.add(newType);
			return types;
		}
		return Json.toJson(Arrays.asList(newType));
	}

	private <T> Stream<T> asStream(Iterator<T> elements) {
		final Iterable<T> iterable = () -> elements;
		return StreamSupport.stream(iterable.spliterator(), false);
	}
}
