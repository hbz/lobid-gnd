package models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.common.geo.GeoPoint;

import controllers.HomeController;
import models.EntityFacts.Link;

public class AuthorityResource {

	final static String DNB_PREFIX = "http://d-nb.info/gnd/";

	private String id;
	private List<String> type;

	public Map<String, Object> definition;
	public Map<String, Object> biographicalOrHistoricalInformation;
	public List<Map<String, Object>> hasGeometry;
	public List<String> gndIdentifier;
	public String preferredName;
	public List<String> variantName;
	public List<String> sameAs;
	public List<String> geographicAreaCode;
	public List<String> gndSubjectCategory;
	public List<String> relatedTerm;
	public List<String> relatedPerson;
	public List<String> relatedWork;
	public List<String> broaderTermInstantial;
	public List<String> broaderTermGeneral;
	public List<String> broaderTermGeneric;
	public List<String> broaderTermPartitive;
	public List<String> dateOfConferenceOrEvent;
	public List<String> placeOfConferenceOrEvent;
	public List<String> spatialAreaOfActivity;
	public List<String> dateOfEstablishment;
	public List<String> placeOfBusiness;
	public List<String> wikipedia;
	public List<String> homepage;
	public List<String> topic;
	public List<String> gender;
	public List<String> professionOrOccupation;
	public List<String> precedingPlaceOrGeographicName;
	public List<String> succeedingPlaceOrGeographicName;
	public List<String> dateOfTermination;
	public List<String> academicDegree;
	public List<String> acquaintanceshipOrFriendship;
	public List<String> familialRelationship;
	public List<String> placeOfActivity;
	public List<String> dateOfBirth;
	public List<String> placeOfBirth;
	public List<String> placeOfDeath;
	public List<String> dateOfDeath;
	public List<String> professionalRelationship;
	public List<String> hierarchicalSuperiorOfTheCorporateBody;
	public List<String> firstAuthor;
	public List<String> publication;
	public List<String> dateOfProduction;
	public List<String> mediumOfPerformance;
	public List<String> firstComposer;
	public List<String> dateOfPublication;
	public EntityFacts entityFacts;

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
		add("type", typeLinks(), fields);
		add("gndIdentifier", gndIdentifier, fields);
		add("definition", definition, fields);
		add("biographicalOrHistoricalInformation", biographicalOrHistoricalInformation, fields);
		add("firstAuthor", firstAuthor, fields);
		add("firstComposer", firstComposer, fields);
		add("mediumOfPerformance", mediumOfPerformance, fields);
		add("professionOrOccupation", professionOrOccupation, fields);
		add("homepage", homepage, fields);
		add("academicDegree", academicDegree, fields);
		add("geographicAreaCode", geographicAreaCode, fields);
		add("gndSubjectCategory", gndSubjectCategory, fields);
		add("topic", topic, fields);
		add("hierarchicalSuperiorOfTheCorporateBody", hierarchicalSuperiorOfTheCorporateBody, fields);
		add("broaderTermPartitive", broaderTermPartitive, fields);
		add("broaderTermInstantial", broaderTermInstantial, fields);
		add("broaderTermGeneral", broaderTermGeneral, fields);
		add("broaderTermGeneric", broaderTermGeneric, fields);
		add("relatedTerm", relatedTerm, fields);
		add("dateOfConferenceOrEvent", dateOfConferenceOrEvent, fields);
		add("placeOfConferenceOrEvent", placeOfConferenceOrEvent, fields);
		add("relatedPerson", relatedPerson, fields);
		add("professionalRelationship", professionalRelationship, fields);
		add("acquaintanceshipOrFriendship", acquaintanceshipOrFriendship, fields);
		add("familialRelationship", familialRelationship, fields);
		add("placeOfBusiness", placeOfBusiness, fields);
		add("placeOfActivity", placeOfActivity, fields);
		add("spatialAreaOfActivity", spatialAreaOfActivity, fields);
		add("precedingPlaceOrGeographicName", precedingPlaceOrGeographicName, fields);
		add("succeedingPlaceOrGeographicName", succeedingPlaceOrGeographicName, fields);
		add("gender", gender, fields);
		add("dateOfBirth", dateOfBirth, fields);
		add("placeOfBirth", placeOfBirth, fields);
		add("dateOfDeath", dateOfDeath, fields);
		add("placeOfDeath", placeOfDeath, fields);
		add("dateOfEstablishment", dateOfEstablishment, fields);
		add("dateOfTermination", dateOfTermination, fields);
		add("dateOfProduction", dateOfProduction, fields);
		add("dateOfPublication", dateOfPublication, fields);
		add("variantName", variantName, fields);
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
		ArrayList<Link> links = new ArrayList<>(new TreeSet<>(entityFacts.getLinks()));
		List<Pair<String, String>> result = new ArrayList<>();
		if (!links.isEmpty()) {
			String value = links.stream().map(v -> html(v)).collect(Collectors.joining(" | "));
			result.add(Pair.of(GndOntology.label("sameAs"), value));
		}
		return result;
	}

	private String html(Link link) {
		return String.format("<a href='%s'><img src='%s' style='height:1em'/>&nbsp;%s</a>", //
				link.url, link.image, link.label);
	}

	private void add(String label, Map<String, Object> map, List<Pair<String, String>> fields) {
		add(label, map != null ? map.values().stream().map(Object::toString).collect(Collectors.toList()) : null,
				fields);
	}

	private void add(String field, List<String> list, List<Pair<String, String>> result) {
		String label = GndOntology.label(field);
		if (list != null && list.size() > 0) {
			String value = list.stream().map(v -> process(field, label, v)).collect(Collectors.joining(" | "));
			result.add(Pair.of(label, value));
		}
	}

	private String process(String field, String fieldLabel, String value) {
		String label = value;
		String link = value;
		String search = "";
		if (Arrays.asList("wikipedia", "sameAs", "depiction", "homepage").contains(field)) {
			return String.format("<a href='%s'>%s</a>", value, value);
		} else if (value.startsWith("http")) {
			label = GndOntology.label(link);
			link = value.startsWith(DNB_PREFIX)
					? controllers.routes.HomeController.authorityDotFormat(value.replace(DNB_PREFIX, ""), "html")
							.toString()
					: value;
			search = controllers.routes.HomeController.search(field + ":\"" + value + "\"", "", 0, 10, "html")
					.toString();
			String result = String.format("<a title='Weitere Einträge mit %s \"%s\" suchen' href='%s'>%s</a>",
					fieldLabel, label, search, label);
			if (!search.isEmpty()) {
				result = String.format(
						"%s&nbsp;<a title='Linked-Data-Quelle zu \"%s\" anzeigen' href='%s'>"
								+ "<i class='glyphicon glyphicon-link' aria-hidden='true'></i></a>",
						result, label, link);
			}
			return result;
		} else
			return GndOntology.label(value);
	}

}
