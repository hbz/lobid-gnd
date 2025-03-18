/* Copyright 2017-2018 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package models;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.common.geo.GeoPoint;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import controllers.HomeController;
import play.Logger;
import play.libs.Json;
import play.libs.ws.WSClient;
import play.libs.ws.WSResponse;

public class AuthorityResource {
	
	public static final String ID = "AuthorityResource";
	private static final int SHORTEN = 5;
	public static final String DNB_PREFIX = "https://d-nb.info/";
	public static final String RPPD_PREFIX = "https://rppd.lobid.org/";
	public static final String GND_PREFIX = DNB_PREFIX + "gnd/";
	public static final String ELEMENTSET = DNB_PREFIX + "standards/elementset/";
	public static final String VALUE_DELIMITER = "; ";
	private static final List<String> SKIP = Arrays.asList(//
			// handled explicitly:
			"@context", "id", "type", "depiction", "sameAs", "preferredName", "hasGeometry", "definition",
			"biographicalOrHistoricalInformation", //
			// don't display:
			"variantNameEntityForThePerson", "deprecatedUri", "oldAuthorityNumber", "wikipedia", "gender", "rppdId");

	private String id;
	private List<String> type;
	public List<Map<String, Object>> hasGeometry;
	public String preferredName;
	public List<Map<String, Object>> depiction;
	public List<Map<String, Object>> sameAs;
	public List<String> creatorOf;
	public String imageAttribution;
	private JsonNode json;

	private final WSClient httpClient;

	public AuthorityResource(JsonNode json) {
		this(json, null);
	}

	public AuthorityResource(JsonNode json, WSClient httpClient) {
		this.httpClient = httpClient;
		this.json = json;
		this.id = json.get("id").textValue();
		this.type = get("type");
		this.hasGeometry = get("hasGeometry");
		this.preferredName = Optional.ofNullable(json.get("preferredName")).orElse(Json.toJson(getId())).asText();
		this.depiction = get("depiction");
		this.sameAs = get("sameAs");
	}

	@SuppressWarnings("unchecked")
	private <T> List<T> get(String field) {
		JsonNode fieldContent = json.get(field);
		return fieldContent == null ? Collections.emptyList() : Json.fromJson(fieldContent, List.class);
	}

	public String getId() {
		return id.replace(GND_PREFIX, "").replace(RPPD_PREFIX, "");
	}

	public String getFullId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<String> getType() {
		return type.stream().filter(t -> !t.equals(ID)).collect(Collectors.toList());
	}

	public void setType(List<String> type) {
		this.type = type;
	}

	@Override
	public String toString() {
		return "AuthorityResource [id=" + id + "]";
	}

	public String title() {
		return preferredName;
	}
	
	public String lifeDates() {
		return fieldValues("dateOfBirth-dateOfDeath", json)
				.collect(Collectors.joining());
	}

	public String biogramme() {
		return find("definition", "biographicalOrHistoricalInformation");
	}

	private String find(String... fields) {
		for (String field : fields) {
			JsonNode node = json.get(field);
			if (node != null && node.elements().hasNext()) {
				Stream<JsonNode> stream = StreamSupport.stream(
						Spliterators.spliteratorUnknownSize(node.elements(), Spliterator.ORDERED),
						false);
				List<String> collect = stream.map(n -> n.asText().replaceAll("[δ∞]", "\"").replace('Æ', '\''))
						.collect(Collectors.toList());
				return "<p>" + collect.get(0) + "</p>" + collect.subList(1, collect.size()).stream()
						.map(s -> "<h3>Biogramm <small>/ alternativ</small></h3><p>" + s + "</p>")
						.collect(Collectors.joining());
			}
		}
		return "";
	}

	public GeoPoint location() {
		if (hasGeometry.isEmpty())
			return null;
		@SuppressWarnings("unchecked")
		String geoString = ((List<String>) hasGeometry.get(0).get("asWKT")).get(0);
		List<Double> lonLat = scanGeoCoordinates(geoString);
		if (lonLat.size() < 2) {
			throw new IllegalArgumentException("Could not scan geo location from: " + geoString + ", got: " + lonLat);
		}
		return new GeoPoint(lonLat.get(lonLat.size() - 1), lonLat.get(lonLat.size() - 2));
	}

	public List<Pair<String, String>> generalFields() {
		List<Pair<String, String>> fields = new ArrayList<>();
		// addValues("type", typeLinks(), fields);
		addValues("creatorOf", creatorOf, fields);
		addRest(fields);
		List<String> order = HomeController.CONFIG.getStringList("field.order");
		fields.sort((p1, p2) -> {
			int i1 = order.indexOf(p1.getLeft());
			int i2 = order.indexOf(p2.getLeft());
			// order for both fields unspecified, sort by field name:
			if (i1 == -1 && i2 == -1) {
				return p1.getLeft().compareTo(p2.getLeft());
			}
			// sort by order, put unspecified fields after specified fields:
			int end = Integer.MAX_VALUE;
			return Integer.valueOf(i1 == -1 ? end : i1).compareTo(Integer.valueOf(i2 == -1 ? end : i2));
		});
		return fields;
	}

	public List<Pair<String, String>> additionalLinks() {
		ArrayList<LinkWithImage> links = new ArrayList<>(new TreeSet<>(getLinks()));
		List<Pair<String, String>> result = new ArrayList<>();
		if (!links.isEmpty()) {
			String field = "sameAs";
			String value = IntStream.range(0, links.size()).mapToObj(i -> html(field, links, i))
					.collect(Collectors.joining(" | "));
			result.add(Pair.of(field, value));
		}
		return result;
	}

	public List<Pair<String, String>> summaryFields() {
		ArrayList<LinkWithImage> links = new ArrayList<>(new TreeSet<>(getLinks()));
		List<Pair<String, String>> fields = new ArrayList<>();
		addValues("gndIdentifier", Arrays.asList(json.get("gndIdentifier").textValue()), fields);
		addIds("homepage", fields);
		addIds("gndSubjectCategory", fields);
		addIds("geographicAreaCode", fields);
		addValues("variantName", fields);
		if (!links.isEmpty()) {
			String field = "sameAs";
			String value = IntStream.range(0, links.size()).mapToObj(i -> html(field, links, i))
					.collect(Collectors.joining(" | "));
			fields.add(Pair.of(field, value));
		}
		return fields;
	}

	public String gndRelationNodes() {
		List<Map<String, Object>> result = new ArrayList<>();
		addGndEntityNodes(result);
		addGroupingNodes(result);
		return Json.toJson(result).toString();
	}

	public String gndRelationEdges() {
		List<Map<String, Object>> result = new ArrayList<>();
		addDirectConnections(result);
		addGroupedConnections(result);
		return Json.toJson(result).toString();
	}

	private void addGroupingNodes(List<Map<String, Object>> result) {
		gndNodes().stream().filter(pair -> pair.getRight().size() > 1).map(Pair::getLeft).distinct().forEach(rel -> {
			String label = wrapped(GndOntology.label(rel));
			result.add(ImmutableMap.of("id", rel, "shape", "dot", "size", "5", "label", label));
		});
	}

	private void addGndEntityNodes(List<Map<String, Object>> result) {
		result.add(ImmutableMap.of("id", getId(), "label", wrapped(preferredName), "shape", "box"));
		gndNodes().stream().flatMap(pair -> pair.getRight().stream()).distinct()
				.filter(node -> node.get("id") != null && node.get("label") != null).forEach(node -> {
			String id = node.get("id").asText().substring(GND_PREFIX.length());
			String label = wrapped(node.get("label").asText());
			String title = "Details zu " + label + " öffnen";
			result.add(ImmutableMap.of("id", id, "label", label, "shape", "box", "title", title));
		});
	}

	private void addGroupedConnections(List<Map<String, Object>> result) {
		gndNodes().stream().filter(pair -> pair.getRight().size() > 1).forEach(pair -> {
			String rel = pair.getLeft();
			String label = wrapped(GndOntology.label(rel));
			result.add(ImmutableMap.of("from", getId(), "to", rel));
			pair.getRight().stream().filter(node -> node.get("id") != null).forEach(node -> {
				String to = node.get("id").asText().substring(GND_PREFIX.length());
				String title = String.format("Einträge mit %s '%s' suchen", label, GndOntology.label(GND_PREFIX + to));
				String id = rel + "_" + to;
				result.add(ImmutableMap.of("from", rel, "to", to, "arrows", "to", "id", id, "title", title));
			});
		});
	}

	private void addDirectConnections(List<Map<String, Object>> result) {
		gndNodes().stream().filter(pair -> pair.getRight().size() == 1).forEach(pair -> {
			String to = pair.getRight().get(0).get("id").asText().substring(GND_PREFIX.length());
			String rel = pair.getLeft();
			String label = wrapped(GndOntology.label(rel));
			String title = String.format("Einträge mit %s '%s' suchen", label, GndOntology.label(GND_PREFIX + to));
			String id = rel + "_" + to;
			result.add(ImmutableMap.<String, Object>builder().put("from", getId()).put("to", to).put("arrows", "to")
					.put("label", label).put("id", id).put("title", title).build());
		});
	}

	private String wrapped(String s) {
		return s.replaceAll("\\([^)]+\\)", "").replace(" ", "\n");
	}

	private List<Pair<String, List<JsonNode>>> gndNodes() {
		return Lists.newArrayList(json.fieldNames()).stream().filter(key -> {
			JsonNode node = json.get(key);
			return !SKIP.contains(key) && node.isArray() && node.size() > 0 && node.elements().next().isObject()
					&& node.toString().contains(AuthorityResource.GND_PREFIX);
		}).map(key -> Pair.of(key, Lists.newArrayList(json.get(key).elements()).stream().collect(Collectors.toList())))
				.collect(Collectors.toList());
	}

	public LinkWithImage getImage() {
		if (depiction != null && depiction.size() > 0) {
			String url = depiction.get(0).get("url").toString();
			String image = depiction.get(0).get("id").toString();
			Object thumbnail = depiction.get(0).get("thumbnail");
			image = thumbnail != null ? thumbnail.toString() : image;
			return new LinkWithImage(url, image, imageAttribution != null ? imageAttribution : url);
		}
		return new LinkWithImage("", "", "");
	}

	public String dateModified() {
		JsonNode dateModified = json.findValue("dateModified");
		return dateModified != null ? germanDate(dateModified.asText()) : "--";
	}

	private List<Double> scanGeoCoordinates(String geoString) {
		List<Double> lonLat = new ArrayList<Double>();
		try (@SuppressWarnings("resource") // it's the same scanner!
		Scanner s = new Scanner(geoString).useLocale(Locale.US)) {
			while (s.hasNext()) {
				if (s.hasNextDouble()) {
					lonLat.add(s.nextDouble());
				} else {
					s.next();
				}
			}
		}
		return lonLat;
	}

	private void addRest(List<Pair<String, String>> fields) {
		Lists.newArrayList(json.fieldNames()).stream().filter(k -> !SKIP.contains(k)).forEach(key -> {
			JsonNode node = json.get(key);
			switch (node.getNodeType()) {
			case STRING:
				addValues(key, Arrays.asList(json.get(key).textValue()), fields);
				break;
			case ARRAY:
				addArray(key, Lists.newArrayList(node.elements()), fields);
				break;
			case OBJECT:
				if (key.equals("describedBy") && node.get("source") != null) {
					addValues("source", Lists.newArrayList(node.get("source").elements()).stream().map(JsonNode::asText).collect(Collectors.toList()), fields);
					break;
				}
			default:
				Logger.warn("Unexpected JsonNodeType for: {}", node);
				break;
			}
		});
	}

	private void addArray(String key, List<JsonNode> list, List<Pair<String, String>> fields) {
		if (list.size() > 0) {
			JsonNode node = list.get(0);
			switch (node.getNodeType()) {
			case STRING:
				addValues(key, fields);
				break;
			case OBJECT:
				addIds(key, fields);
				break;
			default:
				Logger.warn("Unexpected JsonNodeType for: {}", node);
				break;
			}
		}
	}

	private void addIds(String field, List<Pair<String, String>> result) {
		List<Map<String, Object>> list = get(field);
		add(field, list, result, i -> {
			String id = list.get(i).get("id").toString();
			String label = list.get(i).get("label").toString();
			label = label.matches("\\d.+|http.+") ? GndOntology.label(id) : label;
			return process(field, id, label, i, list.size());
		});
	}

	private void addValues(String field, List<Pair<String, String>> result) {
		// literals are displayed in the same row as their non-literal variants,
		// show literal fields in their own row only if there is no non-literal variant:
		if (!field.endsWith("AsLiteral") || get(field.replace("AsLiteral", "")).isEmpty()) {
			addValues(field, get(field), result);
		}
	}

	private void addValues(String field, List<String> list, List<Pair<String, String>> result) {
		add(field, list, result, i -> process(field, list.get(i), list.get(i), i, list.size()));
	}

	private void add(String field, List<?> list, List<Pair<String, String>> result,
			IntFunction<? extends String> function) {
		try {
			if (list != null && list.size() > 0) {
				String value = IntStream.range(0, list.size()).mapToObj(function).collect(Collectors.joining(" | "));
				value = addLiterals(field, value);
				result.add(Pair.of(field, value));
			}
		} catch (Exception e) {
			Logger.warn("Could not add IDs for field {} in {}", field, json);
			e.printStackTrace();
		}
	}

	private String addLiterals(String field, String result) {
		String literalField = field + "AsLiteral";
		for (Object literal : get(literalField)) {
			String search = controllers.routes.HomeController
					.search("", "", "", "", "", "", literalField + ":\"" + literal + "\"", "", 0,
							10, "html")
					.toString();
			result = result + "&nbsp;" + "|" + "&nbsp;" + literal + "&nbsp;"
					+ String.format(
							"<a title='Weitere Einträge mit %s \"%s\" suchen' href='%s'>"
									+ "<i class='octicon octicon-search' aria-hidden='true'></i></a>",
							GndOntology.label(literalField), literal, search);
		}
		return result;
	}

	private List<String> typeLinks() {
		List<String> subTypes = getType().stream()
				.filter(t -> HomeController.CONFIG.getObject("types").keySet().contains(t))
				.collect(Collectors.toList());
		List<String> typeLinks = (subTypes.isEmpty() ? getType() : subTypes).stream()
				.map(t -> String.format("<a href='%s'>%s</a>",
						controllers.routes.HomeController
								.search("", "", "", "", "", "", "+(type:" + t + ")", "", 0, 10, "")
								.toString(),
						models.GndOntology.label(t)))
				.collect(Collectors.toList());
		return typeLinks;
	}

	private List<LinkWithImage> getLinks() {
		String dnbIcon = "https://portal.dnb.de/favicon.ico";
		String dnbLabel = "Deutsche Nationalbibliothek (DNB)";
		String dnbSubstring = "d-nb.info/gnd";
		JsonNode deprecatedUriNode = json.get("deprecatedUri");
		List<LinkWithImage> result = sameAs == null ? Collections.emptyList()
				: (deprecatedUriNode == null || deprecatedUriNode.size() == 0 ? sameAs.stream()
						: nonDeprecated(deprecatedUriNode))
						.map(map -> {
			String url = map.get("id").toString();
			Object icon = null;
			Object label = null;
			Object collection = map.get("collection");
			if (collection != null) {
				@SuppressWarnings("unchecked")
				Map<String, Object> collectionMap = (Map<String, Object>) collection;
				icon = url.contains(dnbSubstring) ? dnbIcon : collectionMap.get("icon");
				label = collectionMap.get("name");
			}
			return new LinkWithImage(url,
					icon == null
							? "<i class='octicon octicon-link-external text-muted' aria-hidden='true'></i>&nbsp;"
							: icon.toString(),
					label == null ? "" : label.toString());
		}).collect(Collectors.toList());
		if (id.startsWith(GND_PREFIX) && !result.stream().anyMatch(linkWithImage -> linkWithImage.url.contains(dnbSubstring))) {
			result.add(new LinkWithImage(id, dnbIcon, dnbLabel));
		}
		return result;
	}

	private Stream<Map<String, Object>> nonDeprecated(JsonNode deprecatedUriNode) {
		return sameAs.stream()
				.filter(sameAsObject -> !sameAsObject.get("id").equals(deprecatedUriNode.get(0).textValue()));
	}

	private String html(String field, ArrayList<LinkWithImage> links, int i) {
		LinkWithImage link = links.get(i);
		boolean hasImage = !link.image.isEmpty();
		boolean hasLabel = !link.label.isEmpty();
		String label = hasLabel ? link.label : link.url;
		String result = String.format(
				"<a href='%s'>" + (hasImage && !link.image.startsWith("<")
						? "<img src='https://lobid.org/imagesproxy?url=%s' style='height:1em' alt='%s'/>&nbsp;"
						: "%s") + "%s</a>",
				link.url, link.image, label, label);
		return withDefaultHidden(field, links.size(), i, result);
	}

	private String process(String field, String value, String label, int i, int size) {
		String result = label;
		if ("creatorOf".equals(field)) {
			result = String.format("<a href='%s'>%s</a>",
					controllers.routes.HomeController.authority(value.replace(GND_PREFIX, ""), null), label);
		} else if (Arrays.asList("wikipedia", "sameAs", "depiction", "homepage").contains(field)) {
			result = String.format("<a href='%s'>%s</a>", value, value);
		} else if (Arrays.asList("dateOfBirth", "dateOfDeath").contains(field)) {
			result = germanDate(value);
		}
		else if (value.startsWith("http")) {
			String url = value;
			String rest = "";
			if (value.contains(" ")) {
				value.substring(0, value.indexOf(' '));
				rest = value.substring(value.indexOf(' ') + 1, value.length());
			}
			List<String> facets = Arrays.asList(HomeController.AGGREGATIONS);
			boolean labelBasedFacet = facets.contains(field + ".label");
			boolean qBasedSearch = facets.stream().noneMatch(s -> s.startsWith(field));
			boolean plainUriField = field.equals("source") || field.equals("publication");
			String searchField = (field + (plainUriField ? "" : ".id")).replace("source",
					"describedBy.source");
			label = plainUriField ? labelFor(url) : label;
			String search = controllers.routes.HomeController
					.search(qBasedSearch ? searchField + ":\"" + url + "\"" : "", "", "", "", "", "",
							labelBasedFacet ? field + ".label:\"" + label + "\""
							: searchField + ":\"" + url + "\"", "", 0, 10, "html")
					.toString();
			String searchLink = String.format(
					"<a id='%s-%s' title='Weitere Einträge mit %s \"%s\" suchen' href='%s'>%s</a>", //
					field, i, GndOntology.label(field), label, search, label);
			String entityLink = String.format(
					"<a title='Linked-Data-Quelle zu \"%s\" anzeigen' href='%s'>"
							+ "<i class='octicon octicon-link text-muted' aria-hidden='true'></i></a>",
					label, url);
			boolean linkableEntity = field.equals("relatedPerson") || plainUriField
					|| (field.startsWith("place") && url.contains("spatial"));
			result = searchLink + "&nbsp;" + (linkableEntity ? entityLink : "") + " " + rest;
		} else if (field.endsWith("AsLiteral")) {
			String search = controllers.routes.HomeController
					.search("", "", "", "", "", "", field + ":\"" + value + "\"", "", 0, 10, "html")
					.toString();
			result = result + "&nbsp;"
					+ String.format(
							"<a title='Weitere Einträge mit %s \"%s\" suchen' href='%s'>"
									+ "<i class='octicon octicon-search' aria-hidden='true'></i></a>",
							GndOntology.label(field), value, search);
		}
		return withDefaultHidden(field, size, i, result);
	}

	private String labelFor(String uri) {
		try {
			JsonNode response = httpClient.url(uri).setFollowRedirects(true)
					.addQueryParameter("format", "json").get().thenApply(WSResponse::asJson)
					.toCompletableFuture().get();
			JsonNode entity = response.has("member") ? response.get("member").elements().next()
					: response;
			return entity.get("title").asText();
		} catch (InterruptedException | ExecutionException e) {
			Logger.error("Could not get label for {}", uri);
			e.printStackTrace();
		}
		return uri;
	}

	public static String germanDate(String value) {
		try {
			return LocalDate
					.from(DateTimeFormatter.ISO_LOCAL_DATE.parse(cleanDate(value)))
					.format(DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.GERMAN));
		} catch (DateTimeParseException e) {
			Logger.warn("Non-ISO date: {}", value);
			return value;
		}

	}

	public static String cleanDate(String value) {
		return value.replaceAll(".*(\\d{4}-\\d{2}-\\d{2}).*", "$1");
	}

	private String withDefaultHidden(String field, int size, int i, String result) {
		if (i == SHORTEN) {
			result = String.format("<span id='%s-hide-by-default' style='display: none;'>", field.replace(".id", ""))
					+ result;
		}
		if (i >= SHORTEN && i == size - 1) {
			result = result + "</span>";
		}
		return result;
	}

	public static Stream<String> fieldValues(String field, JsonNode document) {
		// standard case: `field` is a plain field name, use that:
		List<String> result = flatStrings(document.findValues(field));
		if (result.isEmpty()) {
			// `label_fieldName` template, e.g. `*_dateOfBirth`
			if (field.contains("_")) {
				Matcher matcher = Pattern.compile("([^_]+)_([A-Za-z]+)").matcher(field);
				while (matcher.find()) {
					String label = matcher.group(1);
					String fieldName = matcher.group(2);
					List<JsonNode> findValues = document.findValues(fieldName);
					if (!findValues.isEmpty()) {
						String values = flatStrings(findValues).stream()
								.collect(Collectors.joining(VALUE_DELIMITER));
						field = field.replace(matcher.group(), label + " " + values);
					} else {
						field = field.replace(matcher.group(), "");
					}
				}
				result = field.trim().isEmpty() ? Arrays.asList() : Arrays.asList(field);
			}
			// date ranges, e.g. `dateOfBirth-dateOfDeath`
			else if (field.contains("-")) {
				String[] fields = field.split("-");
				String v1 = year(document.findValue(fields[0]));
				String v2 = year(document.findValue(fields[1]));
				result = v1.isEmpty() && v2.isEmpty() ? Lists.newArrayList()
						: Arrays.asList(String.format("%s-%s", v1, v2));
			}
		}
		return result.stream();
	}

	private static List<String> flatStrings(List<JsonNode> values) {
		return values.stream().flatMap(node -> toArray(node)).map(node -> toString(node))
				.collect(Collectors.toList());
	}

	private static Stream<JsonNode> toArray(JsonNode node) {
		return node.isArray() ? Lists.newArrayList(node.elements()).stream()
				: Arrays.asList(node).stream();
	}

	private static String toString(JsonNode node) {
		return year((node.isTextual() ? Optional.ofNullable(node)
				: Optional.ofNullable(node.findValue("label"))).orElseGet(() -> Json.toJson(""))
						.asText());
	}

	private static String year(JsonNode node) {
		if (node == null || !node.isArray() || node.size() == 0) {
			return "";
		}
		return year(node.elements().next().asText());
	}

	private static String year(String text) {
		return text.matches(".*\\d{4}.*") ? text.replaceAll(".*(\\d{4}).*", "$1")
				: text.replaceAll(".*(\\d{3}).*", "$1");
	}
}
