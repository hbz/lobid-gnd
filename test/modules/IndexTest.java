package modules;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import apps.Convert;
import controllers.HomeController;
import models.AuthorityResource;
import models.GndOntology;
import play.Application;
import play.Logger;
import play.api.inject.BindingKey;
import play.api.inject.DefaultApplicationLifecycle;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;
import play.test.WithApplication;

public class IndexTest extends WithApplication {

	private static final boolean USE_LOCALHOST_CONTEXT_URL = false;
	private static final File[] TEST_FILES = new File("test/ttl").listFiles();
	private static final String PATH = "GND.jsonl";

	private IndexComponent index;

	@Override
	protected Application provideApplication() {
		// See
		// https://www.playframework.com/documentation/2.6.1/JavaDependencyInjection
		// https://www.playframework.com/documentation/2.6.x/JavaTestingWithGuice
		return new GuiceApplicationBuilder().build();
	}

	@BeforeClass
	public static void convert() throws IOException {
		try (FileWriter out = new FileWriter(PATH)) {
			for (File file : TEST_FILES) {
				Model sourceModel = ModelFactory.createDefaultModel();
				sourceModel.read(new FileReader(file), null, "TTL");
				String id = file.getName().split("\\.")[0];
				String jsonLd = Convert.toJsonLd(id, sourceModel, USE_LOCALHOST_CONTEXT_URL);
				String meta = Json.toJson(
						ImmutableMap.of("index", ImmutableMap.of("_index", "gnd", "_type", "authority", "_id", id)))
						.toString();
				out.write(meta + "\n");
				out.write(jsonLd + "\n");
			}
		}
	}

	@Before
	public void setup() {
		String indexName = HomeController.config("index.name");
		index = app.injector().instanceOf(new BindingKey<>(IndexComponent.class));
		ElasticsearchServer.deleteIndex(index.client(), indexName);
		app.injector().instanceOf(DefaultApplicationLifecycle.class).stop();
		index = app.injector().instanceOf(new BindingKey<>(IndexComponent.class));
		index.client().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
	}

	@Test
	public void testIndexData() {
		Assert.assertTrue("Index data file should exist", new File(PATH).exists());
		Logger.info("Indexed from: " + PATH);
	}

	@Test
	public void testTotalHits() {
		Assert.assertEquals(TEST_FILES.length, index.query("*").getHits().getTotalHits());
	}

	@Test
	public void testFieldQuery() {
		Assert.assertEquals(1, index.query("böll").getHits().getTotalHits());
		Assert.assertEquals(1, index.query("preferredName:\"Weizenbaum, Joseph\"").getHits().getTotalHits());
		Assert.assertEquals(0, index.query("id:\"Weizenbaum, Joseph\"").getHits().getTotalHits());
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

}
