package apps;

import static apps.Convert.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import modules.IndexComponent;
import modules.IndexTest;
import play.Logger;

@RunWith(Parameterized.class)
public class IndexAppTest extends IndexTest {

	private static IndexComponent index;
	private static String indexName = "test";

	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { //
				{ "test/data/GND.jsonl", 5L }, //
				{ "test/data/index", 53L } });
	}

	private static String testInput;
	private static Long expectedHits;

	public IndexAppTest(String input, Long hits) {
		testInput = input;
		expectedHits = hits;
	}

	@Before
	public void setUpIndex() throws IOException {
		index = Index.index;
		Index.deleteIndex(indexName);
		Index.index(indexName, index.client(), testInput, config("index.delete.baseline"));
	}

	@Test
	public void testIndexExists() {
		MatchAllQueryBuilder query = QueryBuilders.matchAllQuery();
		Long totalHits = index.client().prepareSearch(indexName).setQuery(query).get().getHits().getTotalHits();
		Logger.info("HITS for {}: {}", testInput, totalHits);
		Assert.assertEquals(expectedHits, totalHits);
	}

}
