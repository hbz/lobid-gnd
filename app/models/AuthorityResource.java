package models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeSet;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.common.geo.GeoPoint;

import controllers.HomeController;

public class AuthorityResource {

	private static final int SHORTEN = 10;

	public final static String DNB_PREFIX = "http://d-nb.info/gnd/";

	public static class Link implements Comparable<Link> {
		public String url;
		public String image;
		public String label;

		Link(String url, String icon, String label) {
			this.url = url;
			this.image = icon;
			this.label = label.replace("Gemeinsame Normdatei (GND) im Katalog der Deutschen Nationalbibliothek",
					"Deutsche Nationalbibliothek (DNB)");
		}

		@Override
		public int hashCode() {
			return url.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Link that = (Link) obj;
			return that.url.equals(this.url);
		}

		@Override
		public String toString() {
			return "Link [url=" + url + ", icon=" + image + ", label=" + label + "]";
		}

		@Override
		public int compareTo(Link that) {
			return that.url.compareTo(this.url);
		}
	}

	private String id;
	private List<String> type;

	public List<String> definition;
	public List<String> biographicalOrHistoricalInformation;
	public List<Map<String, Object>> hasGeometry;
	public String gndIdentifier;
	public String preferredName;
	public List<String> variantName;
	public List<Map<String, Object>> sameAs;
	public List<Map<String, Object>> depiction;
	public List<Map<String, Object>> geographicAreaCode;
	public List<Map<String, Object>> gndSubjectCategory;
	public List<Map<String, Object>> relatedTerm;
	public List<Map<String, Object>> relatedPerson;
	public List<Map<String, Object>> relatedWork;
	public List<Map<String, Object>> broaderTermInstantial;
	public List<Map<String, Object>> broaderTermGeneral;
	public List<Map<String, Object>> broaderTermGeneric;
	public List<Map<String, Object>> broaderTermPartitive;
	public List<String> dateOfConferenceOrEvent;
	public List<Map<String, Object>> placeOfConferenceOrEvent;
	public List<Map<String, Object>> spatialAreaOfActivity;
	public List<String> dateOfEstablishment;
	public List<Map<String, Object>> placeOfBusiness;
	public List<Map<String, Object>> wikipedia;
	public List<Map<String, Object>> homepage;
	public List<Map<String, Object>> topic;
	public List<Map<String, Object>> gender;
	public List<Map<String, Object>> professionOrOccupation;
	public List<Map<String, Object>> precedingPlaceOrGeographicName;
	public List<Map<String, Object>> succeedingPlaceOrGeographicName;
	public List<String> dateOfTermination;
	public List<String> academicDegree;
	public List<Map<String, Object>> acquaintanceshipOrFriendship;
	public List<Map<String, Object>> familialRelationship;
	public List<Map<String, Object>> placeOfActivity;
	public List<String> dateOfBirth;
	public List<Map<String, Object>> placeOfBirth;
	public List<Map<String, Object>> placeOfDeath;
	public List<String> dateOfDeath;
	public List<Map<String, Object>> professionalRelationship;
	public List<Map<String, Object>> hierarchicalSuperiorOfTheCorporateBody;
	public List<Map<String, Object>> firstAuthor;
	public List<String> publication;
	public List<String> dateOfProduction;
	public List<Map<String, Object>> mediumOfPerformance;
	public List<Map<String, Object>> firstComposer;
	public List<String> dateOfPublication;
	public List<Map<String, Object>> affiliation;
	public List<Map<String, Object>> formOfWorkAndExpression;
	public List<Map<String, Object>> addressee;

	public List<String> creatorOf;

	public String imageAttribution;

	public String getId() {
		return id.substring(DNB_PREFIX.length());
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<String> getType() {
		return type.stream().filter(t -> !t.equals("AuthorityResource")).collect(Collectors.toList());
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

	public GeoPoint location() {
		if (hasGeometry == null)
			return null;
		@SuppressWarnings("unchecked")
		String geoString = ((List<Map<String, Object>>) hasGeometry.get(0).get("asWKT")).get(0).get("@value")
				.toString();
		List<Double> lonLat = scanGeoCoordinates(geoString);
		if (lonLat.size() != 2) {
			throw new IllegalArgumentException("Could not scan geo location from: " + geoString + ", got: " + lonLat);
		}
		return new GeoPoint(lonLat.get(1), lonLat.get(0));
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

	public List<Pair<String, String>> generalFields() {
		List<Pair<String, String>> fields = new ArrayList<>();
		addValues("type", typeLinks(), fields);
		addValues("gndIdentifier", Arrays.asList(gndIdentifier), fields);
		addValues("definition", definition, fields);
		addValues("biographicalOrHistoricalInformation", biographicalOrHistoricalInformation, fields);
		addIds("firstAuthor", firstAuthor, fields);
		addIds("firstComposer", firstComposer, fields);
		addIds("mediumOfPerformance", mediumOfPerformance, fields);
		addIds("professionOrOccupation", professionOrOccupation, fields);
		addIds("affiliation", affiliation, fields);
		addIds("homepage", homepage, fields);
		addValues("academicDegree", academicDegree, fields);
		addIds("geographicAreaCode", geographicAreaCode, fields);
		addIds("gndSubjectCategory", gndSubjectCategory, fields);
		addIds("topic", topic, fields);
		addIds("hierarchicalSuperiorOfTheCorporateBody", hierarchicalSuperiorOfTheCorporateBody, fields);
		addIds("broaderTermPartitive", broaderTermPartitive, fields);
		addIds("broaderTermInstantial", broaderTermInstantial, fields);
		addIds("broaderTermGeneral", broaderTermGeneral, fields);
		addIds("broaderTermGeneric", broaderTermGeneric, fields);
		addIds("relatedTerm", relatedTerm, fields);
		addValues("dateOfConferenceOrEvent", dateOfConferenceOrEvent, fields);
		addIds("placeOfConferenceOrEvent", placeOfConferenceOrEvent, fields);
		addIds("relatedPerson", relatedPerson, fields);
		addIds("professionalRelationship", professionalRelationship, fields);
		addIds("acquaintanceshipOrFriendship", acquaintanceshipOrFriendship, fields);
		addIds("familialRelationship", familialRelationship, fields);
		addIds("placeOfBusiness", placeOfBusiness, fields);
		addIds("placeOfActivity", placeOfActivity, fields);
		addIds("spatialAreaOfActivity", spatialAreaOfActivity, fields);
		addIds("precedingPlaceOrGeographicName", precedingPlaceOrGeographicName, fields);
		addIds("succeedingPlaceOrGeographicName", succeedingPlaceOrGeographicName, fields);
		addIds("gender", gender, fields);
		addValues("dateOfBirth", dateOfBirth, fields);
		addValues("dateOfDeath", dateOfDeath, fields);
		addIds("placeOfBirth", placeOfBirth, fields);
		addIds("placeOfDeath", placeOfDeath, fields);
		addValues("dateOfEstablishment", dateOfEstablishment, fields);
		addValues("dateOfTermination", dateOfTermination, fields);
		addValues("dateOfProduction", dateOfProduction, fields);
		addValues("dateOfPublication", dateOfPublication, fields);
		addValues("variantName", variantName, fields);
		addValues("creatorOf", creatorOf, fields);
		addIds("formOfWorkAndExpression", formOfWorkAndExpression, fields);
		addIds("addressee", addressee, fields);
		return fields;
	}

	private List<String> typeLinks() {
		List<String> subTypes = getType().stream()
				.filter(t -> HomeController.CONFIG.getObject("types").keySet().contains(t))
				.collect(Collectors.toList());
		List<String> typeLinks = (subTypes.isEmpty() ? getType() : subTypes).stream()
				.map(t -> String.format("<a href='%s'>%s</a>",
						controllers.routes.HomeController.search("", "+(type:" + t + ")", 0, 10, "").toString(),
						models.GndOntology.label(t)))
				.collect(Collectors.toList());
		return typeLinks;
	}

	public List<Pair<String, String>> additionalLinks() {
		ArrayList<Link> links = new ArrayList<>(new TreeSet<>(getLinks()));
		List<Pair<String, String>> result = new ArrayList<>();
		if (!links.isEmpty()) {
			String field = "sameAs";
			String value = IntStream.range(0, links.size()).mapToObj(i -> html(field, links, i))
					.collect(Collectors.joining(" | "));
			result.add(Pair.of(field, value));
		}
		return result;
	}

	private List<Link> getLinks() {
		return sameAs == null ? Collections.emptyList() : sameAs.stream().map(map -> {
			String url = map.get("id").toString();
			Object icon = null;
			Object label = null;
			Object collection = map.get("collection");
			if (collection != null) {
				@SuppressWarnings("unchecked")
				Map<String, Object> collectionMap = (Map<String, Object>) collection;
				icon = collectionMap.get("icon");
				label = collectionMap.get("name");
			}
			return new Link(url, icon == null ? "" : icon.toString(), label == null ? "" : label.toString());
		}).collect(Collectors.toList());
	}

	public Link getImage() {
		if (depiction != null && depiction.size() > 0) {
			String url = depiction.get(0).get("url").toString();
			String image = depiction.get(0).get("id").toString();
			Object thumbnail = depiction.get(0).get("thumbnail");
			image = thumbnail != null ? thumbnail.toString() : image;
			return new Link(url, image, imageAttribution != null ? imageAttribution : url);
		}
		return new Link("", "", "");
	}

	private String html(String field, ArrayList<Link> links, int i) {
		Link link = links.get(i);
		String result = String.format("<a href='%s'><img src='%s' style='height:1em'/>&nbsp;%s</a>", //
				link.url, link.image, link.label);
		return withDefaultHidden(field, links.size(), i, result);
	}

	private void addIds(String field, List<Map<String, Object>> list, List<Pair<String, String>> result) {
		add(field, list, result, i -> {
			String id = list.get(i).get("id").toString();
			String label = list.get(i).get("label").toString();
			return process(field, id, label, i, list.size());
		});
	}

	private void addValues(String field, List<String> list, List<Pair<String, String>> result) {
		add(field, list, result, i -> process(field, list.get(i), list.get(i), i, list.size()));
	}

	private void add(String field, List<?> list, List<Pair<String, String>> result,
			IntFunction<? extends String> function) {
		if (list != null && list.size() > 0) {
			String value = IntStream.range(0, list.size()).mapToObj(function).collect(Collectors.joining(" | "));
			result.add(Pair.of(field, value));
		}
	}

	private String process(String field, String value, String label, int i, int size) {
		String result = label;
		if ("creatorOf".equals(field)) {
			result = String.format("<a href='%s'>%s</a>",
					controllers.routes.HomeController.authority(value.replace(DNB_PREFIX, ""), null), label);
		} else if (Arrays.asList("wikipedia", "sameAs", "depiction", "homepage").contains(field)) {
			result = String.format("<a href='%s'>%s</a>", value, value);
		} else if (value.startsWith("http")) {
			String link = value.startsWith(DNB_PREFIX)
					? controllers.routes.HomeController.authorityDotFormat(value.replace(DNB_PREFIX, ""), "html")
							.toString()
					: value;
			String search = controllers.routes.HomeController.search(field + ".id:\"" + value + "\"", "", 0, 10, "html")
					.toString();
			String entityLink = String.format(
					"<a id='%s-%s' title='Linked-Data-Quelle zu \"%s\" anzeigen' href='%s'>%s</a>", //
					field, i, label, link, label);
			String searchLink = String.format(
					"<a title='Weitere Einträge mit %s \"%s\" suchen' href='%s'>"
							+ "<i class='octicon octicon-search' aria-hidden='true'></i></a>",
					GndOntology.label(field), label, search);
			result = entityLink + "&nbsp;" + searchLink;
		}
		return withDefaultHidden(field, size, i, result);
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
}
