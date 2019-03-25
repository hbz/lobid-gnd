/* Copyright 2017-2018 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package apps;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.culturegraph.mf.framework.ObjectReceiver;
import org.culturegraph.mf.framework.helpers.DefaultStreamPipe;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;

import controllers.HomeController;
import models.AuthorityResource;
import models.GndOntology;
import play.Logger;
import play.libs.Json;

public class Convert {

	static final Config CONFIG = ConfigFactory.parseFile(new File("conf/application.conf"));

	static final TransportClient CLIENT = new PreBuiltTransportClient(
			Settings.builder().put("cluster.name", HomeController.config("index.cluster")).build());

	static {
		ConfigFactory.parseFile(new File("conf/application.conf")).getStringList("index.hosts").forEach((host) -> {
			try {
				CLIENT.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), 9300));
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		});
	}

	static String config(String id) {
		return CONFIG.getString(id);
	}

	static final Map<String, Object> context = load();

	static class ToAuthorityJson extends DefaultStreamPipe<ObjectReceiver<String>> {

		private final XPath xPath = XPathFactory.newInstance().newXPath();
		Set<String> deprecated = new HashSet<>();

		@Override
		public void literal(String name, String value) {
			String id = null;
			try {
				id = xPath.evaluate(
						"/*[local-name() = 'RDF']/*[local-name() = 'Description']/*[local-name() = 'gndIdentifier']",
						new InputSource(new BufferedReader(new StringReader(value))));
			} catch (XPathExpressionException e) {
				Logger.warn("XPath evaluation failed for {}: {}", name, e.getMessage());
				return;
			}
			Model model = sourceModel(value);
			String jsonLd = Convert.toJsonLd(id, model, false, deprecated);
			getReceiver().process(jsonLd);
		}

		private static Model sourceModel(String rdf) {
			Model sourceModel = ModelFactory.createDefaultModel();
			sourceModel.read(new BufferedReader(new StringReader(rdf)), null, "RDF/XML");
			return sourceModel;
		}

	}

	public static String toJsonLd(String id, Model sourceModel, boolean dev, Set<String> deprecated) {
		String contextUrl = dev ? config("context.dev") : config("context.prod");
		ImmutableMap<String, String> frame = ImmutableMap.of("@type", config("data.superclass"), "@embed", "@always");
		JsonLdOptions options = new JsonLdOptions();
		options.setCompactArrays(true);
		options.setProcessingMode("json-ld-1.1");
		try {
			Model model = preprocess(sourceModel, id, deprecated);
			StringWriter out = new StringWriter();
			RDFDataMgr.write(out, model, Lang.JSONLD);
			Object jsonLd = JsonUtils.fromString(out.toString());
			jsonLd = JsonLdProcessor.frame(jsonLd, new HashMap<>(frame), options);
			jsonLd = JsonLdProcessor.compact(jsonLd, context, options);
			return postprocess(id, contextUrl, jsonLd);
		} catch (JsonLdError | IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private static Model preprocess(Model model, String id, Set<String> deprecated) {
		String academicDegree = "http://d-nb.info/standards/elementset/gnd#academicDegree";
		String dateOfBirth = "http://d-nb.info/standards/elementset/gnd#dateOfBirth";
		String dateOfDeath = "http://d-nb.info/standards/elementset/gnd#dateOfDeath";
		String sameAs = "http://www.w3.org/2002/07/owl#sameAs";
		String preferredName = "http://d-nb.info/standards/elementset/gnd#preferredNameFor";
		String variantName = "http://d-nb.info/standards/elementset/gnd#variantNameFor";
		String type = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
		String label = "http://www.w3.org/2000/01/rdf-schema#label";
		String gnd = "http://d-nb.info/standards/elementset/gnd#";
		String collection = "http://d-nb.info/standards/elementset/dnb#isDescribedIn";
		String deprecatedUri = "http://d-nb.info/standards/elementset/dnb#deprecatedUri";
		String describedBy = "http://www.w3.org/2007/05/powder-s#describedby";
		Set<Statement> toRemove = new HashSet<>();
		Set<Statement> toAdd = new HashSet<>();
		model.listStatements().forEachRemaining(statement -> {
			String s = statement.getSubject().toString();
			String p = statement.getPredicate().toString();
			RDFNode o = statement.getObject();
			if (p.equals(deprecatedUri) && !s.equals(o.toString())) {
				deprecated.add(o.toString().substring(AuthorityResource.DNB_PREFIX.length()));
			}
			if (o.isURIResource()) {
				if (s.equals(o.toString())) { // remove self-ref statements
					toRemove.add(statement);
				} else {
					if (p.equals(sameAs)) {
						// Add `collection` details for `sameAs`
						// See https://github.com/hbz/lobid-gnd/issues/69
						Statement collectionStatement = model.createStatement(model.createResource(o.toString()),
								model.createProperty(collection), model.createResource(collectionId(o.toString())));
						toAdd.add(collectionStatement);
						toAdd.addAll(collectionDetails(o.toString(), model));
					} else if (!p.equals(describedBy)) {
						// Add `label` statement for any link
						// See https://github.com/hbz/lobid-gnd/issues/85
						// See https://github.com/hbz/lobid-gnd/issues/24
						Statement labelStatement = model//
								.createLiteralStatement(//
										model.createResource(o.toString()), //
										model.createProperty(label), //
										GndOntology.label(o.toString()));
						toAdd.add(labelStatement);
					}
				}
			}
			if (p.equals(academicDegree) && o.isURIResource()) {
				// See https://github.com/hbz/lobid-gnd/commit/2cb4b9b
				replaceObjectLiteral(model, statement, o.toString(), toRemove, toAdd);
			} else if ((p.equals(dateOfBirth) || p.equals(dateOfDeath)) //
					&& o.isLiteral() && o.asLiteral().getDatatypeURI() != null) {
				// See https://github.com/hbz/lobid-gnd/commit/2cb4b9b
				replaceObjectLiteral(model, statement, o.asLiteral().getString(), toRemove, toAdd);
			} else if ((p.equals(sameAs)) //
					&& o.isLiteral() && o.asLiteral().getDatatypeURI() != null) {
				// See https://github.com/hbz/lobid-gnd/commit/00ca2a6
				toRemove.add(statement);
				toAdd.add(model.createStatement(statement.getSubject(), statement.getPredicate(),
						model.createResource(o.asLiteral().getString())));
			} else if (p.startsWith(preferredName) || p.startsWith(variantName)) {
				// See https://github.com/hbz/lobid-gnd/issues/3
				toRemove.add(statement);
				String general = p//
						.replaceAll("preferredNameFor[^\"]+", "preferredName")
						.replaceAll("variantNameFor[^\"]+", "variantName");
				toAdd.add(model.createStatement(statement.getSubject(), model.createProperty(general), o));
			} else if (p.equals(type) && o.toString().startsWith(gnd)) {
				// https://github.com/hbz/lobid-gnd/issues/1#issuecomment-312597639
				if (s.equals("http://d-nb.info/gnd/" + id)) {
					toAdd.add(model.createStatement(statement.getSubject(), statement.getPredicate(),
							model.createResource(config("data.superclass"))));
				}
				// See https://github.com/hbz/lobid-gnd/issues/2
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

	private static List<Statement> collectionDetails(String resourceId, Model model) {
		Map<String, Object> collections = CONFIG.getObject("collections").unwrapped();
		for (String key : collections.keySet()) {
			if (resourceId.startsWith(key)) {
				@SuppressWarnings("unchecked")
				List<String> details = (List<String>) collections.get(key);
				List<String> properties = CONFIG.getStringList("collections.properties");
				List<Statement> result = IntStream.range(0, properties.size())
						.mapToObj(i -> model.createStatement(//
								model.createResource(details.get(0)), //
								model.createProperty(properties.get(i)), //
								model.createLiteral(details.get(i + 1))))
						.collect(Collectors.toList());
				return result;
			}
		}
		Logger.warn("No collection details found for {}", resourceId);
		return Collections.emptyList();

	}

	private static String secondLevelTypeFor(String gnd, String type) {
		String key = type.substring(gnd.length());
		ConfigObject object = CONFIG.getObject("types");
		return object.containsKey(key) ? gnd + object.get(key).unwrapped().toString() : null;
	}

	private static void replaceObjectLiteral(Model model, Statement statement, String newObjectLiteral,
			Set<Statement> toRemove, Set<Statement> toAdd) {
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

	private static String postprocess(String id, String contextUrl, Object jsonLd) {
		JsonNode in = Json.toJson(jsonLd);
		JsonNode graph = in.get("@graph");
		JsonNode first = graph == null ? in : graph.elements().next();
		JsonNode result = in;
		if (first.isObject()) {
			@SuppressWarnings("unchecked") /* first.isObject() */
			Map<String, Object> res = Json.fromJson(first, TreeMap.class);
			res.put("@context", contextUrl);
			result = Json.toJson(res);
		}
		return Json.stringify(withEntityFacts(id, result));
	}

	private static JsonNode withEntityFacts(String id, JsonNode node) {
		JsonNode result = node;
		try {
			GetResponse response = CLIENT
					.prepareGet(config("index.entityfacts.index"), config("index.entityfacts.type"), id).execute()
					.actionGet();
			if (response.isExists()) {
				JsonNode json = Json.parse(response.getSourceAsString());
				JsonNode depiction = json.get("depiction");
				JsonNode sameAs = json.get("sameAs");
				Map<String, Object> map = addIfExits(result, depiction, sameAs);
				result = Json.toJson(map);
			}
		} catch (Exception e) {
			Logger.warn("Could not enrich {} from EntityFacts: {}", id, e.getMessage());
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> addIfExits(JsonNode result, JsonNode depiction, JsonNode sameAs) {
		Map<String, Object> map = Json.fromJson(result, Map.class);
		if (sameAs != null) {
			List<Map<String, Object>> fromJson = Json.fromJson(sameAs, List.class);
			List<JsonNode> labelled = fromJson.stream().map((Map<String, Object> sameAsMap) -> {
				Map<String, Object> collection = (Map<String, Object>) sameAsMap.get("collection");
				collection.put("id", collectionId(sameAsMap.get("@id").toString()));
				sameAsMap.put("id", sameAsMap.get("@id"));
				sameAsMap.remove("@id");
				return Json.toJson(sameAsMap);
			}).collect(Collectors.toList());
			if (!map.containsKey("sameAs")) {
				map.put("sameAs", labelled);
			} else {
				((List<JsonNode>) map.get("sameAs")).addAll(labelled);
			}
		}
		if (depiction != null) {
			map.put("depiction",
					Arrays.asList(ImmutableMap.of(//
							"id", depiction.get("@id"), //
							"url", depiction.get("url"), //
							"thumbnail", depiction.get("thumbnail").get("@id"))));
		}
		return map;
	}

	private static String collectionId(String id) {
		Map<String, Object> collections = CONFIG.getObject("collections").unwrapped();
		for (String key : collections.keySet()) {
			if (id.startsWith(key)) {
				@SuppressWarnings("unchecked")
				String collectionId = ((List<String>) collections.get(key)).get(0);
				return collectionId;
			}
		}
		URI uri = URI.create(id);
		return uri.getScheme() + "://" + uri.getHost();
	}

}
