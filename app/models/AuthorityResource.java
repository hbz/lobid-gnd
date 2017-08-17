package models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.elasticsearch.action.get.GetResponse;

import controllers.HomeController;
import modules.IndexComponent;
import play.Logger;

public class AuthorityResource {

	private final static String DNB_PREFIX = "http://d-nb.info/gnd/";

	public enum Values {
		JOINED, MULTI_LINE
	}

	public IndexComponent index;

	private String id;
	private List<String> type;

	public Map<String, Object> definition;
	public Map<String, Object> biographicalOrHistoricalInformation;
	public List<Map<String, Object>> hasGeometry;
	public List<String> gndIdentifier;
	public List<String> preferredName;
	public List<String> variantName;
	public List<String> sameAs;
	public List<String> geographicAreaCode;
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
		return preferredName.get(0);
	}

	public String subtitle() {
		return getType().stream().collect(Collectors.joining("; "));
	}

	public List<Pair<String, String>> generalFields() {
		List<Pair<String, String>> fields = new ArrayList<>();
		add("Bevorzugter Name", preferredName, Values.JOINED, fields);
		add("Varianter Name", variantName, Values.JOINED, fields);
		add("Entitätstyp", getType(), Values.JOINED, fields);
		add("GND-ID", gndIdentifier, Values.JOINED, fields);
		add("Ländercode", geographicAreaCode, Values.MULTI_LINE, fields);
		add("Siehe auch", sameAs != null
				? sameAs.stream().filter(v -> !v.startsWith(DNB_PREFIX)).collect(Collectors.toList()) : sameAs,
				Values.MULTI_LINE, fields);
		return fields;
	}

	public List<Pair<String, String>> specialFields() {
		List<Pair<String, String>> fields = new ArrayList<>();
		@SuppressWarnings("unchecked")
		Map<String, Object> location = hasGeometry != null
				? ((List<Map<String, Object>>) hasGeometry.get(0).get("asWKT")).get(0) : null;
		add("Definition", definition, Values.JOINED, fields);
		add("Oberbegriff partitiv", broaderTermPartitive, Values.MULTI_LINE, fields);
		add("Oberbegriff instantiell", broaderTermInstantial, Values.MULTI_LINE, fields);
		add("Oberbegriff allgemein", broaderTermGeneral, Values.MULTI_LINE, fields);
		add("Verwandter Begriff", relatedTerm, Values.MULTI_LINE, fields);
		add("Veranstalungsdaten", dateOfConferenceOrEvent, Values.MULTI_LINE, fields);
		add("Veranstaltungsort", placeOfConferenceOrEvent, Values.MULTI_LINE, fields);
		add("Geographischer Wirkungsbereich", spatialAreaOfActivity, Values.MULTI_LINE, fields);
		add("In Beziehung stehende Person", relatedPerson, Values.MULTI_LINE, fields);
		add("Gründungsdatum", dateOfEstablishment, Values.JOINED, fields);
		add("Sitz", placeOfBusiness, Values.MULTI_LINE, fields);
		add("Wikipedia", wikipedia, Values.JOINED, fields);
		add("Thema", topic, Values.JOINED, fields);
		add("Homepage", homepage, Values.JOINED, fields);
		add("Biografische oder historische Angaben", biographicalOrHistoricalInformation, Values.JOINED, fields);
		add("Geschlecht", gender, Values.MULTI_LINE, fields);
		add("Beruf oder Beschäftigung", professionOrOccupation, Values.MULTI_LINE, fields);
		add("Vorheriges Geografikum", precedingPlaceOrGeographicName, Values.MULTI_LINE, fields);
		add("Nachfolgendes Geografikum", succeedingPlaceOrGeographicName, Values.MULTI_LINE, fields);
		add("Auflösungsdatum", dateOfTermination, Values.JOINED, fields);
		add("Akademischer Grad", academicDegree, Values.JOINED, fields);
		add("Bekannt mit", acquaintanceshipOrFriendship, Values.MULTI_LINE, fields);
		add("Beziehung, Bekanntschaft, Freundschaft", familialRelationship, Values.MULTI_LINE, fields);
		add("Wirkungsort", placeOfActivity, Values.MULTI_LINE, fields);
		add("Geburtsdatum", dateOfBirth, Values.JOINED, fields);
		add("Geburtsort", placeOfBirth, Values.JOINED, fields);
		add("Sterbedatum", dateOfDeath, Values.JOINED, fields);
		add("Sterbeort", placeOfDeath, Values.JOINED, fields);
		add("In Beziehung stehendes Werk", relatedWork, Values.MULTI_LINE, fields);
		add("Beruflich Beziehung", professionalRelationship, Values.MULTI_LINE, fields);
		add("Erste Verfasserschaft", firstAuthor, Values.MULTI_LINE, fields);
		add("Administrative Überordnung der Körperschaft", hierarchicalSuperiorOfTheCorporateBody, Values.MULTI_LINE,
				fields);
		add("Titelangabe", publication, Values.MULTI_LINE, fields);
		add("Erstellungszeit", dateOfProduction, Values.MULTI_LINE, fields);
		add("Besetzung im Musikbereich", mediumOfPerformance, Values.MULTI_LINE, fields);
		add("Erster Komponist", firstComposer, Values.MULTI_LINE, fields);
		add("Erscheinungszeit", dateOfPublication, Values.MULTI_LINE, fields);
		add("Ort", location, Values.MULTI_LINE, fields);
		return fields;
	}

	private void add(String label, Map<String, Object> map, Values joined, List<Pair<String, String>> fields) {
		add(label, map != null ? map.values().stream().map(Object::toString).collect(Collectors.toList()) : null,
				joined, fields);
	}

	private void add(String label, List<String> list, Values values, List<Pair<String, String>> result) {
		if (list != null && list.size() > 0) {
			switch (values) {
			case JOINED: {
				String value = list.stream().map(v -> process(v)).collect(Collectors.joining("; "));
				result.add(Pair.of(label, value));
				break;
			}
			case MULTI_LINE: {
				result.add(Pair.of(label, process(list.get(0))));
				list.subList(1, list.size()).forEach(e -> {
					result.add(Pair.of("", process(e)));
				});
				break;
			}
			}
		}
	}

	private String process(String string) {
		String label = string;
		String link = string;
		if (string.startsWith(DNB_PREFIX)) {
			label = labelFor(string.substring(DNB_PREFIX.length()));
			link = string.replace(DNB_PREFIX, "/authorities/") + ".html";
			if (label == null) {
				label = string;
				link = string;
			}
		}
		return string.startsWith("http") ? String.format("<a href='%s'>%s</a>", link, label) : link;
	}

	public String labelFor(String id) {
		GetResponse response = index.client()
				.prepareGet(HomeController.config("index.name"), HomeController.config("index.type"), id).get();
		if (!response.isExists()) {
			Logger.warn("{} does not exists in index", id);
			return null;
		}
		@SuppressWarnings("unchecked")
		List<String> preferredName = (List<String>) response.getSourceAsMap().get("preferredName");
		return preferredName.get(0).toString();
	}

}
