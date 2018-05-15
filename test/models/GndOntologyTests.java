package models;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GndOntologyTests {

	@Parameters(name = "{0} -> {1}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { //
				{ "CollectiveManuscript", "Sammelhandschrift" }, //
				{ "https://d-nb.info/standards/elementset/gnd#CollectiveManuscript", "Sammelhandschrift" }, //
				{ "broaderTermPartitive", "Oberbegriff partitiv" }, //
				{ "preferredName", "Bevorzugter Name" }, //
				{ "gndIdentifier", "GND-Nummer" }, //
				{ "ZZ", "Sonstiges Ausland, Unbekanntes Land" }, //
				{ "http://d-nb.info/standards/vocab/gnd/gnd-sc#ZZ", "Sonstiges Ausland, Unbekanntes Land" }, //
				{ "36", "Basteln, Handarbeiten, Heimwerken" }, //
				{ "http://d-nb.info/standards/vocab/gnd/geographic-area-code#36", "Basteln, Handarbeiten, Heimwerken" }, //
				{ "notKnown", "Unbekannt" }, //
				{ "http://d-nb.info/standards/vocab/gnd/gender#notKnown", "Unbekannt" }, //
				{ "hasChild", "Kind" }, //
				{ "hasSpouse", "Ehepartner" }, //
				{ "hasAncestor", "Vorfahr" }, //
				{ "broadMatch", "Oberbegriff" }, //
				{ "exactMatch", "Entspricht" }, //
				{ "relatedMatch", "Verwandter Begriff" } });
	}

	private String id;
	private String label;

	public GndOntologyTests(String id, String label) {
		this.id = id;
		this.label = label;
	}

	@Test
	public void testLabel() throws FileNotFoundException {
		assertThat(GndOntology.label(id), equalTo(label));
	}

}
