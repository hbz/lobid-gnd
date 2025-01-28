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
public class GndOntologyLabelTests {

	@Parameters(name = "{0} -> {1}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { //
				{ "CollectiveManuscript", "Sammelhandschrift" }, //
				{ "https://d-nb.info/standards/elementset/gnd#CollectiveManuscript", "Sammelhandschrift" }, //
				{ "broaderTermPartitive", "Oberbegriff partitiv" }, //
				{ "preferredName", "Bevorzugter Name" }, //
				{ "gndIdentifier", "GND-Nummer" }, //
				{ "ZZ", "Land unbekannt" }, //
				{ "https://d-nb.info/standards/vocab/gnd/gnd-sc#ZZ", "Land unbekannt" }, //
				{ "XA-CH", "Schweiz" }, //
				{ "https://d-nb.info/standards/vocab/gnd/gnd-sc#XA-CH", "Schweiz" }, //
				{ "36", "Basteln, Handarbeiten, Heimwerken" }, //
				{ "https://d-nb.info/standards/vocab/gnd/geographic-area-code#36", "Basteln, Handarbeiten, Heimwerken" }, //
				{ "notKnown", "Unbekannt" }, //
				{ "https://d-nb.info/standards/vocab/gnd/gender#notKnown", "Unbekannt" }, //
				{ "hasChild", "Kind" }, //
				{ "hasSpouse", "Ehepartner" }, //
				{ "hasAncestor", "Vorfahr" }, //
				{ "hasParent", "Elternteil" }, //
				{ "professionalRelationship", "Berufliche Beziehung" }, //
				{ "closeMatch", "Gleichwertig" }, //
				{ "broadMatch", "Oberbegriff" }, //
				{ "narrowMatch", "Unterbegriff" },//
				{ "exactMatch", "Entspricht" }, //
				{ "relatedMatch", "Verwandter Begriff" }, //
				{ "https://d-nb.info/standards/vocab/gnd/description-level#3", "Katalogisierungslevel 3" }, //
				{ "SubjectHeadingSensoStricto", "Schlagwort sensu stricto" } });
	}

	private String id;
	private String label;

	public GndOntologyLabelTests(String id, String label) {
		this.id = id;
		this.label = label;
	}

	@Test
	public void testLabel() throws FileNotFoundException {
		assertThat(GndOntology.label(id), equalTo(label));
	}

}
