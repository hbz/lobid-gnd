package apps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import play.libs.Json;

public class ConvertTest {

	@Test
	public void testConvertBaseline() {
		String output = "test/GND.jsonl";
		File file = new File(output);
		if (file.exists()) {
			file.delete();
		}
		ConvertBaseline.main(new String[] { "test/GND.rdf", output });
		assertTrue("Output should exist", file.exists());
	}

	@Test
	public void testGeneralNames() throws FileNotFoundException {
		String jsonLd = jsonLdFor("100002617");
		// Replace literal fields with general fields:
		assertFalse(jsonLd.contains("preferredNameForThePerson"));
		assertFalse(jsonLd.contains("variantNameForThePerson"));
		assertTrue(jsonLd.contains("preferredName"));
		assertTrue(jsonLd.contains("variantName"));
		// Don't replace entity fields:
		assertTrue(jsonLd.contains("preferredNameEntityForThePerson"));
		assertTrue(jsonLd.contains("variantNameEntityForThePerson"));
	}

	@Test
	public void testPreferredNameIsTextual() throws FileNotFoundException {
		String jsonLd = jsonLdFor("100002617");
		JsonNode jsonNode = Json.parse(jsonLd);
		assertTrue(jsonNode.get("preferredName").isTextual());
	}

	@Test
	public void testgndIdentifierIsTextual() throws FileNotFoundException {
		String jsonLd = jsonLdFor("7855044-0");
		JsonNode gndIdentifier = Json.parse(jsonLd).get("gndIdentifier");
		assertTrue(gndIdentifier.isTextual());
	}

	@Test
	public void testSecondLevelTypePerson() throws FileNotFoundException {
		String jsonLd = jsonLdFor("100002617");
		List<?> types = Json.fromJson(Json.parse(jsonLd).get("type"), List.class);
		assertTrue(types.contains("AuthorityResource"));
		assertTrue(types.contains("Person"));
	}

	@Test
	public void testSecondLevelTypePlace() throws FileNotFoundException {
		String jsonLd = jsonLdFor("4074335-4");
		List<?> types = Json.fromJson(Json.parse(jsonLd).get("type"), List.class);
		assertTrue(types.contains("AuthorityResource"));
		assertTrue(types.contains("PlaceOrGeographicName"));
	}

	@Test
	public void testSpirit() throws FileNotFoundException {
		String jsonLd = jsonLdFor("7855044-0");
		List<?> types = Json.fromJson(Json.parse(jsonLd).get("type"), List.class);
		assertTrue(types.contains("Spirits"));
	}

	@Test
	public void testGeographicAreaCodeIsArray() throws FileNotFoundException {
		String jsonLd = jsonLdFor("7855044-0");
		assertTrue(Json.parse(jsonLd).get("geographicAreaCode").isArray());
	}

	@Test
	@Ignore // FIXME: fix issue, in jsonld-java/jena?
	public void testPlaceOfBirthObjects() throws FileNotFoundException {
		String jsonLd = jsonLdFor("118820591");
		assertTrue(Json.parse(jsonLd).get("placeOfBirth").elements().next().isObject());
	}

	@Test
	@Ignore // FIXME: fix issue, in jsonld-java/jena?
	public void testPlaceOfDeathObjects() throws FileNotFoundException {
		String jsonLd = jsonLdFor("118820591");
		assertTrue(Json.parse(jsonLd).get("placeOfDeath").elements().next().isObject());
	}

	@Test
	public void testIriFieldStructure() throws FileNotFoundException {
		String jsonLd = jsonLdFor("7855044-0");
		JsonNode terms = Json.parse(jsonLd).get("broaderTermGeneral");
		assertTrue(terms.isArray());
		assertTrue(terms.elements().hasNext());
		JsonNode first = terms.elements().next();
		assertTrue(first.isObject());
		assertTrue(first.has("id"));
		assertEquals("http://d-nb.info/gnd/4074854-6", first.get("id").textValue());
		assertTrue(first.has("label"));
		JsonNode label = first.get("label");
		assertTrue(label.isTextual());
		assertTrue(!label.toString().isEmpty());
	}

	private String jsonLdFor(String id) throws FileNotFoundException {
		Model sourceModel = ModelFactory.createDefaultModel();
		sourceModel.read(new FileReader("test/ttl/" + id + ".ttl"), null, "TTL");
		String jsonLd = Convert.toJsonLd(id, sourceModel, true);
		return jsonLd;
	}
}
