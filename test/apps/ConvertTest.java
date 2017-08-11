package apps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;

import org.junit.Test;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;
import play.test.WithApplication;

public class ConvertTest extends WithApplication {

	@Override
	protected Application provideApplication() {
		return new GuiceApplicationBuilder().build();
	}

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
	public void testSecondLevelTypePerson() throws FileNotFoundException {
		String jsonLd = jsonLdFor("100002617");
		assertTrue(Json.fromJson(Json.parse(jsonLd).get("type"), List.class).contains("Person"));
	}

	@Test
	public void testSecondLevelTypePlace() throws FileNotFoundException {
		String jsonLd = jsonLdFor("4074335-4");
		assertTrue(Json.fromJson(Json.parse(jsonLd).get("type"), List.class).contains("PlaceOrGeographicName"));
	}

	private String jsonLdFor(String id) throws FileNotFoundException {
		Model sourceModel = ModelFactory.createDefaultModel();
		sourceModel.read(new FileReader("test/ttl/" + id + ".ttl"), null, "TTL");
		String jsonLd = Convert.toJsonLd(id, sourceModel, true);
		return jsonLd;
	}
}
