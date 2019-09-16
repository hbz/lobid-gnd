package modules;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Set;

import org.elasticsearch.action.search.SearchPhaseExecutionException;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import models.AuthorityResource;
import models.GndOntology;
import play.Logger;
import play.libs.Json;

public class IndexQueryTest extends IndexTest {

	@Test
	public void testIndexData() {
		Assert.assertTrue("Index data file should exist", new File(PATH).exists());
		Logger.info("Indexed from: " + PATH);
	}

	@Test
	public void testTotalHits() {
		Assert.assertEquals(TTL_TEST_FILES.length, index.query("*").getHits().getTotalHits());
	}

	@Test
	public void testFieldQuery() {
		Assert.assertEquals(1, index.query("böll").getHits().getTotalHits());
		Assert.assertEquals(1, index.query("preferredName:\"Weizenbaum, Joseph\"").getHits().getTotalHits());
		Assert.assertEquals(1, index.query("preferredName:\"weizenbaum, joseph\"").getHits().getTotalHits());
		Assert.assertEquals(0, index.query("id:\"Weizenbaum, Joseph\"").getHits().getTotalHits());
		Assert.assertEquals(2, index.query("preferredName:\"Erdmann, Elisabeth von\"").getHits().getTotalHits());
	}

	@Test
	public void testDateQuery() {
		Assert.assertEquals(15, index.query("dateOfBirth:*").getHits().getTotalHits());
		Assert.assertEquals(5, index.query("dateOfBirth:[* TO 1750]").getHits().getTotalHits());
		Assert.assertEquals(9, index.query("dateOfDeath:*").getHits().getTotalHits());
		Assert.assertEquals(2, index.query("dateOfDeath:[* TO 1750]").getHits().getTotalHits());
		Assert.assertEquals(2, index.query("dateOfPublication:*").getHits().getTotalHits());
		Assert.assertEquals(1, index.query("dateOfPublication:[1950 TO 1960]").getHits().getTotalHits());
	}

	@Test
	public void testBooleanSearch() {
		Assert.assertEquals(1, index.query("twain schriftsteller").getHits().getTotalHits());
		Assert.assertEquals(1, index.query("twain AND schriftsteller").getHits().getTotalHits());
		Assert.assertEquals(7, index.query("twain OR schriftsteller").getHits().getTotalHits());
	}

	@Test
	public void testSlashInQuery() {
		Assert.assertEquals(1, index.query("Hauptschule / Lehrer").getHits().getTotalHits());
		Assert.assertEquals(1, index.query("/[Hh]auptschulen?/").getHits().getTotalHits());
	}

	@Test
	public void testContextQuery() {
		Assert.assertEquals(0, index.query("context.jsonld").getHits().getTotalHits());
	}

	@Test
	public void testPerfectFieldMatch() {
		SearchResponse response = index.query("london");
		Assert.assertEquals(2, response.getHits().getTotalHits());
		AuthorityResource resource = new AuthorityResource(
				Json.parse(response.getHits().getHits()[0].getSourceAsString()));
		Assert.assertEquals("London", resource.preferredName);
	}

	@Test
	public void testBoosting() {
		Assert.assertEquals("118624822", firstHit(index.query("Mark Twain")));
		Assert.assertEquals("118624822", firstHit(index.query("118624822")));
	}

	private String firstHit(SearchResponse searchResponse) {
		Assert.assertFalse("Hits should not be empty for response: " + searchResponse.toString(),
				searchResponse.getHits().getTotalHits() == 0);
		JsonNode json = Json.parse(searchResponse.getHits().getAt(0).getSourceAsString());
		JsonNode id = json.get("gndIdentifier");
		Assert.assertNotNull("First hit should have an ID", id);
		return id.asText();
	}

	@Test
	public void testAggregations() {
		Set<String> keySet = index.query("*").getAggregations().asMap().keySet();
		Assert.assertTrue(keySet.contains("type"));
		Assert.assertTrue(keySet.contains("gndSubjectCategory.id"));
		Assert.assertTrue(keySet.contains("geographicAreaCode.id"));
		Assert.assertTrue(keySet.contains("professionOrOccupation.id"));
		Assert.assertTrue(keySet.contains("dateOfBirth"));
	}

	@Test
	public void testGndOntologyIndexLabel() throws FileNotFoundException {
		assertThat(GndOntology.label("http://d-nb.info/gnd/118820591"), equalTo("Weizenbaum, Joseph"));
	}

	@Test
	public void testNgram() {
		SearchResponse response = index.query("weizen");
		Assert.assertEquals(1, response.getHits().getTotalHits());
		AuthorityResource resource = new AuthorityResource(
				Json.parse(response.getHits().getHits()[0].getSourceAsString()));
		Assert.assertEquals("Weizenbaum, Joseph", resource.preferredName);
	}

	@Test
	public void testNoStemming() {
		Assert.assertEquals(0, index.query("namenlose").getHits().getTotalHits());
		Assert.assertEquals(1, index.query("namenlosen").getHits().getTotalHits());
	}

	@Test
	public void testSort() {
		Assert.assertEquals("Abudacnus, Josephus", first("id").preferredName);
		Assert.assertEquals("Aachen", first("preferredName.keyword").preferredName);
		Assert.assertEquals("Aachen", first("preferredName.keyword:asc").preferredName);
		Assert.assertNotEquals("Aachen", first("preferredName.keyword:desc").preferredName);
	}

	private AuthorityResource first(String sort) {
		return new AuthorityResource(
				Json.parse(index.query("*", "", sort, 0, 10).getHits().getHits()[0].getSourceAsString()));
	}

	@Test(expected = SearchPhaseExecutionException.class)
	public void testInvalidQuery() {
		index.query("++test");
	}

}
