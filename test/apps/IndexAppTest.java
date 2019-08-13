package apps;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IndexAppTest {

	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { //
				{ "test/data/GND.jsonl" }, //
				{ "test/data/index" } });
	}

	private String input;

	public IndexAppTest(String input) {
		this.input = input;
	}

	@Before
	public void testConvertBaseline() {
		Index.main(new String[] { input });
	}

	@Test
	public void testIndexExists() {
		Assert.assertTrue(Index.index.query("*").getHits().getTotalHits() > 0);
	}

}
