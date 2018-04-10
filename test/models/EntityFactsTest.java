package models;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import play.libs.ws.WSClient;
import play.test.WSTestClient;

/**
 * Tests for the {@link EntityFacts} class.
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class EntityFactsTest {

	private static final WSClient CLIENT = WSTestClient.newClient(9300);
	private static final EntityFacts LONDON = EntityFacts.entity(CLIENT, "4074335-4");

	@Test
	public void testJsonByNumberAllFields() {
		assertThat(LONDON, notNullValue());
	}

	@Test
	public void testNotFound() {
		assertThat(EntityFacts.entity(CLIENT, "London"), notNullValue());
	}

	@Test
	public void testJsonByNumberMissingFields() {
		assertThat(EntityFacts.entity(CLIENT, "141568992"), notNullValue());
	}

	@Test
	public void testJsonByUri() {
		assertThat(EntityFacts.entity(CLIENT, "http://d-nb.info/gnd/4074335-4"), notNullValue());
	}

	@Test
	public void testLinks() {
		assertThat(LONDON.getLinks(), hasItem("https://de.wikisource.org/wiki/London"));
	}

	@Test
	public void testImage() {
		assertThat(LONDON.getImage(), startsWith("https://commons.wikimedia.org/wiki/Special:FilePath/"));
	}

}
