package apps;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.test.WithApplication;

public class ConvertTest extends WithApplication {

	@Override
	protected Application provideApplication() {
		return new GuiceApplicationBuilder().build();
	}

	@Test
	public void testConvert() {
		String output = "test/GND.jsonl";
		File file = new File(output);
		if (file.exists()) {
			file.delete();
		}
		Convert.main(new String[] { "test/GND.rdf", output });
		assertTrue("Output should exist", file.exists());
	}

}
