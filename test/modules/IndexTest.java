package modules;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

import apps.Convert;
import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;
import play.test.WithApplication;

public class IndexTest extends WithApplication {

	private static final String PATH = "GND.jsonl";

	@Override
	protected Application provideApplication() {
		// See
		// https://www.playframework.com/documentation/2.6.1/JavaDependencyInjection
		return new GuiceApplicationBuilder().build();
	}

	@BeforeClass
	public static void convert() throws IOException {
		try (FileWriter out = new FileWriter(PATH)) {
			for (File file : new File("test/ttl").listFiles()) {
				Model sourceModel = ModelFactory.createDefaultModel();
				sourceModel.read(new FileReader(file), null, "TTL");
				String id = file.getName().split("\\.")[0];
				String jsonLd = Convert.toJsonLd(id, sourceModel, true);
				String meta = Json.toJson(ImmutableMap.of("index",
						ImmutableMap.of("_index", "authorities", "_type", "authority", "_id", id))).toString();
				out.write(meta + "\n");
				out.write(jsonLd + "\n");
			}
		}
	}

	@AfterClass
	public static void cleanup() throws IOException {
		// TODO: use separate dir, override config in provideApplication
		// FileUtils.deleteDirectory(new File("data"));
	}

	@Test
	public void test() {
		Assert.assertTrue("Index data file should exist", new File(PATH).exists());
		System.out.println("Indexed from: " + PATH);
		// TODO: inject index, check number of docs
	}

}
