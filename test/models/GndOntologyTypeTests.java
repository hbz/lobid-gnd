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
public class GndOntologyTypeTests {

	@Parameters(name = "{0} -> {1}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { //
				{ "professionOrOccupation", "SubjectHeading" }, //
				{ "placeOfBirth", "PlaceOrGeographicName" }, //
				{ "definition", null } });
	}

	private String property;
	private String type;

	public GndOntologyTypeTests(String property, String type) {
		this.property = property;
		this.type = type;
	}

	@Test
	public void testType() throws FileNotFoundException {
		assertThat(GndOntology.type(property), equalTo(type));
	}

}
