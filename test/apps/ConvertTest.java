package apps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.ImmutableMap;

import play.Logger;
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
	public void testPlaceOfBirthObjects() throws FileNotFoundException {
		String jsonLd = jsonLdFor("118820591");
		JsonNode place = Json.parse(jsonLd).get("placeOfBirth").elements().next();
		assertIsObjectWithIdAndLabel(place);
	}

	@Test
	public void testPlaceOfDeathObjects() throws FileNotFoundException {
		String jsonLd = jsonLdFor("118820591");
		JsonNode place = Json.parse(jsonLd).get("placeOfDeath").elements().next();
		assertIsObjectWithIdAndLabel(place);
	}

	@Test
	public void testIriFieldStructure() throws FileNotFoundException {
		String jsonLd = jsonLdFor("7855044-0");
		JsonNode terms = Json.parse(jsonLd).get("broaderTermGeneral");
		assertTrue(terms.isArray());
		assertTrue(terms.elements().hasNext());
		JsonNode first = terms.elements().next();
		assertIsObjectWithIdAndLabel(first);
		assertEquals("http://d-nb.info/gnd/4074854-6", first.get("id").textValue());
		JsonNode label = first.get("label");
		assertTrue(label.isTextual());
		assertTrue(!label.toString().isEmpty());
	}

	@Test
	public void testTriplesToFramedJsonLd() throws FileNotFoundException {
		Model model = ModelFactory.createDefaultModel();
		RDFDataMgr.read(model,
				in("<http://d-nb.info/gnd/118820591> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://d-nb.info/standards/elementset/gnd#AuthorityResource> ."
						+ "<http://d-nb.info/gnd/118820591> <http://d-nb.info/standards/elementset/gnd#placeOfDeath> <http://d-nb.info/gnd/4005728-8> ."
						+ "<http://d-nb.info/gnd/118820591> <http://d-nb.info/standards/elementset/gnd#placeOfBirth> <http://d-nb.info/gnd/4005728-8> ."
						+ "<http://d-nb.info/gnd/4005728-8> <http://www.w3.org/2000/01/rdf-schema#label> \"Berlin\" ."),
				Lang.NTRIPLES);
		StringWriter out = new StringWriter();
		RDFDataMgr.write(out, model, Lang.JSONLD);
		try {
			ImmutableMap<String, String> frame = ImmutableMap.of("@type",
					"http://d-nb.info/standards/elementset/gnd#AuthorityResource", //
					"@embed", "@always");
			JsonLdOptions options = new JsonLdOptions();
			Object jsonLd = JsonUtils.fromString(out.toString());
			jsonLd = JsonLdProcessor.frame(jsonLd, new HashMap<>(frame), options);
			JsonNode jsonNode = Json.toJson(jsonLd);
			JsonNode birth = jsonNode.findValue("http://d-nb.info/standards/elementset/gnd#placeOfBirth");
			JsonNode death = jsonNode.findValue("http://d-nb.info/standards/elementset/gnd#placeOfDeath");
			assertEquals(birth.size(), death.size());
			Logger.info("FRAMED JSON-LD: {}", JsonUtils.toPrettyString(jsonLd));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void assertIsObjectWithIdAndLabel(JsonNode json) {
		assertTrue(json.isObject());
		assertTrue(json.has("id"));
		assertTrue(json.has("label"));
	}

	private InputStream in(String s) {
		return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
	}

	private String jsonLdFor(String id) throws FileNotFoundException {
		Model sourceModel = ModelFactory.createDefaultModel();
		sourceModel.read(new FileReader("test/ttl/" + id + ".ttl"), null, "TTL");
		String jsonLd = Convert.toJsonLd(id, sourceModel, true);
		return jsonLd;
	}
}
