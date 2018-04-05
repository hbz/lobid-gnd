package models;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.io.FileNotFoundException;

import org.junit.Test;

public class GndOntologyTests {

	@Test
	public void testClassLabelShort() throws FileNotFoundException {
		assertThat(GndOntology.label("CollectiveManuscript"), equalTo("Sammelhandschrift"));
	}

	@Test
	public void testClassLabelFull() throws FileNotFoundException {
		assertThat(GndOntology.label("https://d-nb.info/standards/elementset/gnd#CollectiveManuscript"),
				equalTo("Sammelhandschrift"));
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

	@Test
	public void testSubjectCategoryShort() {
		assertThat(GndOntology.label("ZZ"), equalTo("Sonstiges Ausland, Unbekanntes Land"));
	}

	@Test
	public void testSubjectCategoryFull() {
		assertThat(GndOntology.label("http://d-nb.info/standards/vocab/gnd/gnd-sc#ZZ"),
				equalTo("Sonstiges Ausland, Unbekanntes Land"));
	}

	@Test
	public void testGeographicAreaCodeShort() {
		assertThat(GndOntology.label("36"), equalTo("Basteln, Handarbeiten, Heimwerken"));
	}

	@Test
	public void testGeographicAreaCodeFull() {
		assertThat(GndOntology.label("http://d-nb.info/standards/vocab/gnd/geographic-area-code#36"),
				equalTo("Basteln, Handarbeiten, Heimwerken"));
	}

	@Test
	public void testGenderShort() throws FileNotFoundException {
		assertThat(GndOntology.label("notKnown"), equalTo("Unbekannt"));
	}

	@Test
	public void testGenderFull() throws FileNotFoundException {
		assertThat(GndOntology.label("http://d-nb.info/standards/vocab/gnd/gender#notKnown"), equalTo("Unbekannt"));
	}

}
