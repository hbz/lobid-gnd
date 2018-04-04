package models;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.io.FileNotFoundException;

import org.junit.Test;

public class GndOntologyTests {

	@Test
	public void testClassLabel() throws FileNotFoundException {
		assertThat(GndOntology.label("CollectiveManuscript"), equalTo("Sammelhandschrift"));
	}

	@Test
	public void testObjectPropertyLabel() throws FileNotFoundException {
		assertThat(GndOntology.label("broaderTermPartitive"), equalTo("Oberbegriff partitiv"));
	}

	@Test
	public void testAnnotationPropertyLabel() throws FileNotFoundException {
		assertThat(GndOntology.label("preferredName"), equalTo("Bevorzugter Name"));
	}

	@Test
	public void testDatatypePropertyLabel() throws FileNotFoundException {
		assertThat(GndOntology.label("gndIdentifier"), equalTo("GND-Nummer"));
	}

}
