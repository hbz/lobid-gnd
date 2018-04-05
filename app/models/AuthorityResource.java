package models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.common.geo.GeoPoint;

public class AuthorityResource {

	final static String DNB_PREFIX = "http://d-nb.info/gnd/";

	public enum Values {
		JOINED, MULTI_LINE
	}

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

	public String subtitle() {
		return getType().stream().map(t -> GndOntology.label(t)).collect(Collectors.joining("; "));
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
		add("preferredName", Arrays.asList(preferredName), Values.JOINED, fields);
		add("variantName", variantName, Values.JOINED, fields);
		add("type", getType(), Values.JOINED, fields);
		add("gndIdentifier", gndIdentifier, Values.JOINED, fields);
		add("geographicAreaCode", geographicAreaCode, Values.MULTI_LINE, fields);
		add("gndSubjectCategory", gndSubjectCategory, Values.MULTI_LINE, fields);
		add("wikipedia", wikipedia, Values.JOINED, fields);
		add("homepage", homepage, Values.JOINED, fields);
		return fields;
	}

	public List<Pair<String, String>> specialFields() {
		List<Pair<String, String>> fields = new ArrayList<>();
		add("sameAs",
				sameAs != null ? sameAs.stream().filter(v -> !v.startsWith(DNB_PREFIX)).collect(Collectors.toList())
						: sameAs,
				Values.MULTI_LINE, fields);
		add("definition", definition, Values.JOINED, fields);
		add("broaderTermPartitive", broaderTermPartitive, Values.MULTI_LINE, fields);
		add("broaderTermInstantial", broaderTermInstantial, Values.MULTI_LINE, fields);
		add("broaderTermGeneral", broaderTermGeneral, Values.MULTI_LINE, fields);
		add("relatedTerm", relatedTerm, Values.MULTI_LINE, fields);
		add("dateOfConferenceOrEvent", dateOfConferenceOrEvent, Values.MULTI_LINE, fields);
		add("placeOfConferenceOrEvent", placeOfConferenceOrEvent, Values.MULTI_LINE, fields);
		add("spatialAreaOfActivity", spatialAreaOfActivity, Values.MULTI_LINE, fields);
		add("relatedPerson", relatedPerson, Values.MULTI_LINE, fields);
		add("dateOfEstablishment", dateOfEstablishment, Values.JOINED, fields);
		add("placeOfBusiness", placeOfBusiness, Values.MULTI_LINE, fields);
		add("topic", topic, Values.JOINED, fields);
		add("biographicalOrHistoricalInformation", biographicalOrHistoricalInformation, Values.JOINED, fields);
		add("gender", gender, Values.MULTI_LINE, fields);
		add("professionOrOccupation", professionOrOccupation, Values.MULTI_LINE, fields);
		add("precedingPlaceOrGeographicName", precedingPlaceOrGeographicName, Values.MULTI_LINE, fields);
		add("succeedingPlaceOrGeographicName", succeedingPlaceOrGeographicName, Values.MULTI_LINE, fields);
		add("dateOfTermination", dateOfTermination, Values.JOINED, fields);
		add("academicDegree", academicDegree, Values.JOINED, fields);
		add("acquaintanceshipOrFriendship", acquaintanceshipOrFriendship, Values.MULTI_LINE, fields);
		add("familialRelationship", familialRelationship, Values.MULTI_LINE, fields);
		add("placeOfActivity", placeOfActivity, Values.MULTI_LINE, fields);
		add("dateOfBirth", dateOfBirth, Values.JOINED, fields);
		add("placeOfBirth", placeOfBirth, Values.JOINED, fields);
		add("dateOfDeath", dateOfDeath, Values.JOINED, fields);
		add("placeOfDeath", placeOfDeath, Values.JOINED, fields);
		add("relatedWork", relatedWork, Values.MULTI_LINE, fields);
		add("professionalRelationship", professionalRelationship, Values.MULTI_LINE, fields);
		add("firstAuthor", firstAuthor, Values.MULTI_LINE, fields);
		add("hierarchicalSuperiorOfTheCorporateBody", hierarchicalSuperiorOfTheCorporateBody, Values.MULTI_LINE,
				fields);
		add("publication", publication, Values.MULTI_LINE, fields);
		add("dateOfProduction", dateOfProduction, Values.MULTI_LINE, fields);
		add("mediumOfPerformance", mediumOfPerformance, Values.MULTI_LINE, fields);
		add("firstComposer", firstComposer, Values.MULTI_LINE, fields);
		add("dateOfPublication", dateOfPublication, Values.MULTI_LINE, fields);
		return fields;
	}

	private void add(String label, Map<String, Object> map, Values joined, List<Pair<String, String>> fields) {
		add(label, map != null ? map.values().stream().map(Object::toString).collect(Collectors.toList()) : null,
				joined, fields);
	}

	private void add(String field, List<String> list, Values values, List<Pair<String, String>> result) {
		String label = GndOntology.label(field);
		if (list != null && list.size() > 0) {
			switch (values) {
			case JOINED: {
				String value = list.stream().map(v -> process(field, label, v)).collect(Collectors.joining("; "));
				result.add(Pair.of(label, value));
				break;
			}
			case MULTI_LINE: {
				result.add(Pair.of(label, process(field, label, list.get(0))));
				list.subList(1, list.size()).forEach(e -> {
					result.add(Pair.of("", process(field, label, e)));
				});
				break;
			}
			}
		}
	}

	private String process(String field, String fieldLabel, String value) {
		String label = value;
		String link = value;
		String search = "";
		if (value.startsWith("http")) {
			label = GndOntology.label(link);
			link = value.startsWith(DNB_PREFIX)
					? controllers.routes.HomeController.authorityDotFormat(value.replace(DNB_PREFIX, ""), "html")
							.toString()
					: value;
			search = controllers.routes.HomeController.search(field + ":\"" + value + "\"", "", 0, 15, "html")
					.toString();
			String result = String.format("<a title='Weitere Einträge mit %s \"%s\" suchen' href='%s'>%s</a>",
					fieldLabel, label, search, label);
			if (!search.isEmpty()) {
				result = String.format(
						"%s | <a title='Linked-Data-Quelle zu \"%s\" anzeigen' href='%s'>"
								+ "<i class='glyphicon glyphicon-link' aria-hidden='true'></i></a>",
						result, label, link);
			}
			return result;
		} else
			return GndOntology.label(value);
	}

}
