package modules;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.elasticsearch.client.Client;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.google.common.collect.ImmutableMap;

import apps.Convert;
import apps.Index;
import controllers.HomeController;
import play.Logger;
import play.libs.Json;

public class IndexTest {

	private static final boolean USE_LOCALHOST_CONTEXT_URL = false;
	protected static final File[] TTL_TEST_FILES = new File("test/ttl").listFiles();
	protected static final String PATH = HomeController.config("data.jsonlines") + "/GND.jsonl";

	protected static IndexComponent index;

	@BeforeClass
	public static void setUp() throws IOException {
		Logger.info("Converting and indexing test data");
		Index.indexEntityFactsTurtleFiles();
		convertData();
		index = Index.indexBaselineAndUpdates();
		Client client = index.client();
		String bootstrappingIndexName = HomeController.config("index.boot.name");
		Index.createEmptyIndex(client, bootstrappingIndexName, HomeController.config("index.settings"));
		Index.indexData(client, "test/data/index", bootstrappingIndexName);
	}

	@AfterClass
	public static void tearDownBootstrappingIndex() {
		Index.deleteIndex(HomeController.config("index.boot.name"));
	}

	private static void convertData() throws FileNotFoundException, IOException {
		Set<String> deprecated = new HashSet<>();
		try (FileWriter out = new FileWriter(PATH)) {
			for (File file : TTL_TEST_FILES) {
				Model sourceModel = ModelFactory.createDefaultModel();
				sourceModel.read(new FileReader(file), null, "TTL");
				String id = file.getName().split("\\.")[0];
				String jsonLd = Convert.toJsonLd(id, sourceModel, USE_LOCALHOST_CONTEXT_URL, deprecated);
				String meta = Json.toJson(
						ImmutableMap.of("index", ImmutableMap.of("_index", "gnd", "_type", "authority", "_id", id)))
						.toString();
				out.write(meta + "\n");
				out.write(jsonLd + "\n");
			}
		}
		try (PrintWriter pw = new PrintWriter(
				new FileOutputStream(HomeController.config("index.delete.tests"), true))) {
			deprecated.forEach(id -> pw.println(id));
		}
	}

}