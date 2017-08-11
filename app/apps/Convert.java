package apps;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.helpers.DefaultObjectPipe;
import org.culturegraph.mf.framework.helpers.DefaultStreamPipe;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.jena.JenaRDFParser;
import com.google.common.collect.ImmutableMap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;

import ORG.oclc.oai.harvester2.app.RawWrite;
import play.Logger;
import play.libs.Json;

public class Convert {

	private static final Config CONFIG = ConfigFactory.parseFile(new File("conf/application.conf"));

	static String config(String id) {
		return CONFIG.getString(id);
	}

	static final Map<String, Object> context = load();

	static class OpenOaiPmh extends DefaultObjectPipe<String, ObjectReceiver<Reader>> {

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		private String from;
		private String until;

		public OpenOaiPmh(String from, String until) {
			this.from = from;
			this.until = until;
		}

		@Override
		public void process(final String baseUrl) {
			try {
				RawWrite.run(baseUrl, from, until, "RDFxml", "authorities", stream);
				getReceiver().process(
						new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8));
				writeLastSuccessfulUpdate();
			} catch (NoSuchFieldException | IOException | ParserConfigurationException | SAXException
					| TransformerException e) {
				e.printStackTrace();
			}
		}

		private void writeLastSuccessfulUpdate() {
			File file = new File(config("data.updates.last"));
			file.delete();
			try (FileWriter writer = new FileWriter(file)) {
				writer.append(until);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	static class ToString extends DefaultStreamPipe<ObjectReceiver<String>> {
		@Override
		public void literal(String name, String value) {
			getReceiver().process(value);
		}
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
			Model model = preprocess(sourceModel, id);
			Object jsonLd = JsonLdProcessor.fromRDF(model, new JenaRDFParser());
			jsonLd = JsonLdProcessor.frame(jsonLd, new HashMap<>(frame), options);
			jsonLd = JsonLdProcessor.compact(jsonLd, context, options);
			return postprocess(contextUrl, jsonLd);
		} catch (JsonLdError e) {
			e.printStackTrace();
			return null;
		}
	}

	private static Model preprocess(Model model, String id) {
		String academicDegree = "http://d-nb.info/standards/elementset/gnd#academicDegree";
		String dateOfBirth = "http://d-nb.info/standards/elementset/gnd#dateOfBirth";
		String dateOfDeath = "http://d-nb.info/standards/elementset/gnd#dateOfDeath";
		String sameAs = "http://www.w3.org/2002/07/owl#sameAs";
		String preferredName = "http://d-nb.info/standards/elementset/gnd#preferredNameFor";
		String variantName = "http://d-nb.info/standards/elementset/gnd#variantNameFor";
		String type = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
		String gnd = "http://d-nb.info/standards/elementset/gnd#";
		List<Statement> toRemove = new ArrayList<>();
		List<Statement> toAdd = new ArrayList<>();
		model.listStatements().forEachRemaining(statement -> {
			String p = statement.getPredicate().toString();
			RDFNode o = statement.getObject();
			if (p.equals(academicDegree) && o.isURIResource()) {
				replaceObjectLiteral(model, statement, o.toString(), toRemove, toAdd);
			} else if ((p.equals(dateOfBirth) || p.equals(dateOfDeath)) //
					&& o.isLiteral() && o.asLiteral().getDatatypeURI() != null) {
				replaceObjectLiteral(model, statement, o.asLiteral().getString(), toRemove, toAdd);
			} else if ((p.equals(sameAs)) //
					&& o.isLiteral() && o.asLiteral().getDatatypeURI() != null) {
				toRemove.add(statement);
				toAdd.add(model.createStatement(statement.getSubject(), statement.getPredicate(),
						model.createResource(o.asLiteral().getString())));
			} else if (p.startsWith(preferredName) || p.startsWith(variantName)) {
				toRemove.add(statement);
				String general = p//
						.replaceAll("preferredNameFor[^\"]+", "preferredName")
						.replaceAll("variantNameFor[^\"]+", "variantName");
				toAdd.add(model.createStatement(statement.getSubject(), model.createProperty(general), o));
			} else if (p.equals(type) && o.toString().startsWith(gnd)) {
				if (statement.getSubject().toString().equals("http://d-nb.info/gnd/" + id)) {
					toAdd.add(model.createStatement(statement.getSubject(), statement.getPredicate(),
							model.createResource(config("data.superclass"))));
				}
				String newType = secondLevelTypeFor(gnd, o.toString());
				if (newType != null) {
					toAdd.add(model.createStatement(statement.getSubject(), statement.getPredicate(),
							model.createResource(newType)));
				}
			}
		});
		toRemove.stream().forEach(e -> model.remove(e));
		toAdd.stream().forEach(e -> model.add(e));
		return model;
	}

	private static String secondLevelTypeFor(String gnd, String type) {
		String key = type.substring(gnd.length());
		ConfigObject object = CONFIG.getObject("types");
		return object.containsKey(key) ? gnd + object.get(key).unwrapped().toString() : null;
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
}
