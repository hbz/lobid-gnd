package modules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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
		Index.indexEntityFactsJsonFiles();
		convertData();
		index = Index.indexBaselineAndUpdates();
		Client client = index.client();
		createIndex(client, HomeController.config("index.boot.name"));
		createIndex(client, HomeController.config("index.prod.name"));
	}

	private static void createIndex(Client client, String name) throws IOException {
		Index.deleteIndex(name);
		Index.createEmptyIndex(client, name, HomeController.config("index.settings"));
		Index.indexData(client, "test/data/index", name);
	}

	@AfterClass
	public static void deleteIndexes() {
		Index.deleteIndex(HomeController.config("index.boot.name"));
		Index.deleteIndex(HomeController.config("index.prod.name"));
	}

	private static void convertData() throws FileNotFoundException, IOException {
		Set<String> deprecated = new HashSet<>();
		try (FileWriter out = new FileWriter(PATH)) {
			for (File file : TTL_TEST_FILES) {
				Model sourceModel = ModelFactory.createDefaultModel();
				String ttl = Files.readAllLines(Paths.get(file.toURI())).stream().collect(Collectors.joining("\n"));
				sourceModel.read(new BufferedReader(new StringReader(ttl)), null, "TTL");
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