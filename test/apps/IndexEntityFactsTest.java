package apps;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import controllers.HomeController;

public class IndexEntityFactsTest {

	private static final String INDEX_NAME = HomeController.config("index.entityfacts.index");

	@Before
	public void setUp() {
		Index.deleteIndex(INDEX_NAME);
	}

	@Test
	public void testIndexEntityFacts() {
		Index.indexEntityFactsJsonLdDump();
		SearchResponse response = Index.client.prepareSearch(INDEX_NAME).setQuery(QueryBuilders.queryStringQuery("*"))
				.get();
		Assert.assertEquals(10, response.getHits().getTotalHits());
	}
}
