package modules;

import static controllers.HomeController.CONFIG;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;
import org.junit.BeforeClass;

import com.google.common.collect.ImmutableMap;

import apps.Convert;
import controllers.HomeController;
import play.Application;
import play.Logger;
import play.api.inject.BindingKey;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;

public class IndexTest {

	private static final boolean USE_LOCALHOST_CONTEXT_URL = false;
	protected static final File[] TTL_TEST_FILES = new File("test/ttl").listFiles();
	protected static final File[] EF_TEST_FILES = new File("test/entityfacts").listFiles();
	protected static final String PATH = "GND.jsonl";

	protected static IndexComponent index;

	static String indexName = HomeController.config("index.name");

	@BeforeClass
	public static void setUp() throws IOException {
		Logger.info("Converting and indexing test data");
		indexEntityFacts();
		convertData();
		indexData();
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
		try (PrintWriter pw = new PrintWriter(new FileOutputStream(HomeController.config("index.delete"), true))) {
			deprecated.forEach(id -> pw.println(id));
		}
	}

	private static void indexData() {
		Application app = new GuiceApplicationBuilder().build();
		index = app.injector().instanceOf(new BindingKey<>(IndexComponent.class));
		deleteIndex(index.client(), indexName);
		index.startup();
	}

	static void deleteIndex(final Client client, final String index) {
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
		if (ElasticsearchServer.indexExists(client, index)) {
			List<String> hosts = CONFIG.getStringList("index.hosts");
			if (hosts.stream().anyMatch(s -> !s.equals("localhost"))) {
				Assert.fail(String.format("Running tests against remote hosts: '%s', skipping deletion and indexing. "
						+ "Delete index '%s' manually or configure a new index.", hosts, index));
			}
			client.admin().indices().delete(new DeleteIndexRequest(index)).actionGet();
		}
	}

	private static void indexEntityFacts() throws IOException {
		Application app = new GuiceApplicationBuilder().build();
		index = app.injector().instanceOf(new BindingKey<>(IndexComponent.class));
		for (File file : EF_TEST_FILES) {
			String json = Files.lines(Paths.get(file.toURI())).collect(Collectors.joining());
			index.client()
					.prepareIndex(HomeController.config("index.entityfacts.index"),
							HomeController.config("index.entityfacts.type"))
					.setId(file.getName().split("\\.")[0]).setSource(json, XContentType.JSON).execute().actionGet();
		}
		index.client().admin().indices().refresh(new RefreshRequest()).actionGet();
	}
}