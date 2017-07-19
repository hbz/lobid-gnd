package apps;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.culturegraph.mf.elasticsearch.JsonToElasticsearchBulk;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.helpers.DefaultStreamPipe;
import org.culturegraph.mf.io.FileOpener;
import org.culturegraph.mf.io.ObjectWriter;
import org.culturegraph.mf.xml.XmlDecoder;
import org.culturegraph.mf.xml.XmlElementSplitter;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.jena.JenaRDFParser;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.ImmutableMap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import play.Logger;
import play.libs.Json;

public class Convert {

	private static final Config CONFIG = ConfigFactory.parseFile(new File("conf/application.conf"));

	private static String config(String id) {
		return CONFIG.getString(id);
	}

	private static final Map<String, Object> context = load();

	public static void main(String[] args) {
		String input = args.length == 2 ? args[0] : config("data.rdfxml");
		String output = args.length == 2 ? args[1] : config("data.jsonlines");
		FileOpener opener = new FileOpener();
		XmlElementSplitter splitter = new XmlElementSplitter();
		splitter.setElementName("Description");
		splitter.setTopLevelElement("rdf:RDF");
		ToAuthorityJson encodeJson = new ToAuthorityJson();
		JsonToElasticsearchBulk bulk = new JsonToElasticsearchBulk("id", config("index.type"), config("index.name"));
		final ObjectWriter<String> writer = new ObjectWriter<>(output);
		opener.setReceiver(new XmlDecoder()).setReceiver(splitter).setReceiver(encodeJson).setReceiver(bulk)
				.setReceiver(writer);
		opener.process(input);
		opener.closeStream();
	}

	static class ToAuthorityJson extends DefaultStreamPipe<ObjectReceiver<String>> {

		private final XPath xPath = XPathFactory.newInstance().newXPath();

		@Override
		public void literal(String name, String value) {
			String id = null;
			try {
				id = xPath.evaluate(
						"/*[local-name() = 'RDF']/*[local-name() = 'Description']/*[local-name() = 'gndIdentifier']",
						new InputSource(new BufferedReader(new StringReader(value))));
			} catch (XPathExpressionException e) {
				Logger.error("XPath evaluation failed for: {}", value, e);
				return;
			}
			Model model = sourceModel(value);
			String jsonLd = Convert.toJsonLd(id, model, false);
			getReceiver().process(jsonLd);
		}

		private static Model sourceModel(String rdf) {
			Model sourceModel = ModelFactory.createDefaultModel();
			sourceModel.read(new BufferedReader(new StringReader(rdf)), null, "RDF/XML");
			return sourceModel;
		}

	}

	public static String toJsonLd(String id, Model sourceModel, boolean dev) {
		String contextUrl = dev ? config("context.dev") : config("context.prod");
		ImmutableMap<String, String> frame = ImmutableMap.of("@type", config("data.superclass"));
		JsonLdOptions options = new JsonLdOptions();
		options.setCompactArrays(false);
		options.setProcessingMode("json-ld-1.1");
		try {
			Object jsonLd = JsonLdProcessor.fromRDF(fixInconsistentTypes(sourceModel), new JenaRDFParser());
			jsonLd = preprocess(jsonLd, id);
			jsonLd = JsonLdProcessor.frame(jsonLd, new HashMap<>(frame), options);
			jsonLd = JsonLdProcessor.compact(jsonLd, context, options);
			return postprocess(contextUrl, jsonLd);
		} catch (JsonLdError e) {
			e.printStackTrace();
			return null;
		}
	}

	private static Model fixInconsistentTypes(Model model) {
		String academicDegree = "http://d-nb.info/standards/elementset/gnd#academicDegree";
		String dateOfBirth = "http://d-nb.info/standards/elementset/gnd#dateOfBirth";
		String dateOfDeath = "http://d-nb.info/standards/elementset/gnd#dateOfDeath";
		List<Statement> toRemove = new ArrayList<>();
		List<Statement> toAdd = new ArrayList<>();
		model.listStatements().forEachRemaining(statement -> {
			String p = statement.getPredicate().toString();
			RDFNode o = statement.getObject();
			if (p.equals(academicDegree) && o.isURIResource()) {
				replaceObjectLiteral(model, statement, o.toString(), toRemove, toAdd);
			} else if ((p.equals(dateOfBirth) || p.equals(dateOfDeath)) && o.isLiteral()
					&& o.asLiteral().getDatatypeURI() != null) {
				replaceObjectLiteral(model, statement, o.asLiteral().getString(), toRemove, toAdd);
			}
		});
		toRemove.stream().forEach(e -> model.remove(e));
		toAdd.stream().forEach(e -> model.add(e));
		return model;
	}

	private static void replaceObjectLiteral(Model model, Statement statement, String newObjectLiteral,
			List<Statement> toRemove, List<Statement> toAdd) {
		toRemove.add(statement);
		toAdd.add(model.createStatement(statement.getSubject(), statement.getPredicate(),
				model.createLiteral(newObjectLiteral)));
	}

	private static Map<String, Object> load() {
		try {
			JsonNode node = Json
					.parse(Files.lines(Paths.get(config("context.file"))).collect(Collectors.joining("\n")));
			@SuppressWarnings("unchecked")
			Map<String, Object> map = Json.fromJson(node, Map.class);
			return map;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private static Object preprocess(Object jsonLd, String gndId) {
		List<JsonNode> processed = withSuperclass(Json.toJson(jsonLd), gndId);
		try {
			return JsonUtils.fromString(Json.toJson(processed).toString());
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private static String postprocess(String contextUrl, Object jsonLd) {
		JsonNode in = Json.toJson(jsonLd);
		JsonNode graph = in.get("@graph");
		JsonNode first = graph == null ? in : graph.elements().next();
		if (first.isObject()) {
			@SuppressWarnings("unchecked") /* first.isObject() */
			Map<String, Object> res = Json.fromJson(first, TreeMap.class);
			res.put("@context", contextUrl);
			return Json.stringify(Json.toJson(res));
		}
		return Json.stringify(in);
	}

	private static List<JsonNode> withSuperclass(JsonNode json, String id) {
		Stream<JsonNode> stream = asStream(json.elements()).map(e -> {
			if (e.get("@id").toString().contains(id) && e.isObject()) {
				@SuppressWarnings("unchecked") /* e.isObject() */
				Map<String, Object> doc = Json.fromJson(e, Map.class);
				doc.put("@type", addType(doc, config("data.superclass")));
				return Json.toJson(doc);
			} else {
				return e;
			}
		});
		return stream.collect(Collectors.toList());
	}

	private static Object addType(Map<String, Object> doc, String newType) {
		JsonNode json = Json.toJson(doc.get("@type"));
		if (json != null && json.isArray()) {
			@SuppressWarnings("unchecked") /* json.isArray() */
			List<String> types = Json.fromJson(json, List.class);
			types.add(newType);
			return types;
		}
		return Json.toJson(Arrays.asList(newType));
	}

	private static <T> Stream<T> asStream(Iterator<T> elements) {
		final Iterable<T> iterable = () -> elements;
		return StreamSupport.stream(iterable.spliterator(), false);
	}
}
