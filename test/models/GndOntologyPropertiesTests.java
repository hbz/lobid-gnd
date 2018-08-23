package models;

import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GndOntologyPropertiesTests {

	@Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { //
				{ "Family", Arrays.asList("type", "gndIdentifier", "sameAs", "depiction", "gndSubjectCategory",
						"geographicAreaCode", "preferredName", "variantName", "homepage", "languageCode",
						"placeOfActivity", "placeOfExile", "professionOrOccupation", "professionalRelationship",
						"relatedConferenceOrEvent", "complexSeeReferenceSubject", "correspondent",
						"familialRelationship", "fieldOfActivity", "relatedCorporateBody", "relatedFamily",
						"relatedPerson", "relatedPlaceOrGeographicName", "relatedSubjecHeading", "relatedTerm",
						"relatedWork", "affiliationAsLiteral", "biographicalOrHistoricalInformation", "definition",
						"oldAuthorityNumber", "periodOfActivity", "professionOrOccupationAsLiteral", "publication",
						"acquaintanceshipOrFriendship", "affiliation", "relatedSubjectHeading") } });
	}

	private List<String> properties;
	private String type;

	public GndOntologyPropertiesTests(String type, List<String> properties) {
		this.type = type;
		this.properties = properties;
	}

	@Test
	public void testFieldsSpecific() throws FileNotFoundException {
		HashSet<String> expected = new HashSet<String>(properties);
		HashSet<String> actual = new HashSet<String>(GndOntology.properties(type));
		HashSet<String> missing = new HashSet<String>(properties);
		missing.removeAll(actual);
		HashSet<String> excess = new HashSet<String>(actual);
		excess.removeAll(expected);
		assertTrue("Missing: " + missing, missing.isEmpty());
		assertTrue("Excess: " + excess, excess.isEmpty());
	}

	@Test
	public void testFieldsPersonExist() throws FileNotFoundException {
		assertTrue(GndOntology.properties("Person").size() > GndOntology.ADDITIONAL_PROPERTIES.size());
	}

	@Test
	public void testFieldsAll() throws FileNotFoundException {
		assertTrue(GndOntology.properties("AuthorityResource").size() > GndOntology.ADDITIONAL_PROPERTIES.size());
	}

}
