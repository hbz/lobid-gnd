package apps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

import org.junit.Test;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
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
		String id = "100002617";
		Model sourceModel = ModelFactory.createDefaultModel();
		sourceModel.read(new FileReader("test/ttl/" + id + ".ttl"), null, "TTL");
		String jsonLd = Convert.toJsonLd(id, sourceModel, true);
		// Replace literal fields with general fields:
		assertFalse(jsonLd.contains("preferredNameForThePerson"));
		assertFalse(jsonLd.contains("variantNameForThePerson"));
		assertTrue(jsonLd.contains("preferredName"));
		assertTrue(jsonLd.contains("variantName"));
		// Don't replace entity fields:
		assertTrue(jsonLd.contains("preferredNameEntityForThePerson"));
		assertTrue(jsonLd.contains("variantNameEntityForThePerson"));
	}

}
