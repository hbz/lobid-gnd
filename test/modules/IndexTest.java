package modules;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.BeforeClass;

import com.google.common.collect.ImmutableMap;

import apps.Convert;
import controllers.HomeController;
import play.Application;
import play.Logger;
import play.api.inject.BindingKey;
import play.api.inject.DefaultApplicationLifecycle;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;

public class IndexTest {

	private static final boolean USE_LOCALHOST_CONTEXT_URL = false;
	protected static final File[] TEST_FILES = new File("test/ttl").listFiles();
	protected static final String PATH = "GND.jsonl";

	protected static IndexComponent index;

	static String indexName = HomeController.config("index.name");

	@BeforeClass
	public static void setUp() throws IOException {
		Logger.info("Converting and indexing test data");
		convertData();
		indexData();
	}

	private static void convertData() throws FileNotFoundException, IOException {
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

	private static void indexData() {
		Application app = new GuiceApplicationBuilder().build();
		index = app.injector().instanceOf(new BindingKey<>(IndexComponent.class));
		ElasticsearchServer.deleteIndex(index.client(), indexName);
		app.injector().instanceOf(DefaultApplicationLifecycle.class).stop();
		index = app.injector().instanceOf(new BindingKey<>(IndexComponent.class));
		index.client().admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
	}

}
