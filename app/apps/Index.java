package apps;

import static controllers.HomeController.CONFIG;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;

import controllers.HomeController;
import modules.IndexComponent;
import play.Application;
import play.api.inject.BindingKey;
import play.inject.guice.GuiceApplicationBuilder;

public class Index {

	public static IndexComponent index;

	public static void main(String[] args) {
		index = indexData();
	}

	static String indexName = HomeController.config("index.name");

	protected static final File[] ENTITYFACTS_FILES = new File("test/entityfacts").listFiles();

	public static IndexComponent indexData() {
		Application app = new GuiceApplicationBuilder().build();
		IndexComponent index = app.injector().instanceOf(new BindingKey<>(IndexComponent.class));
		deleteIndex(index.client(), indexName);
		index.startup();
		return index;
	}

	static void deleteIndex(final Client client, final String index) {
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
		if (indexExists(client, index)) {
			List<String> hosts = CONFIG.getStringList("index.hosts");
			if (hosts.stream().anyMatch(s -> !s.equals("localhost"))) {
				Assert.fail(String.format("Running against remote hosts: '%s', skipping deletion and indexing. "
						+ "Delete index '%s' manually or configure a new index.", hosts, index));
			}
			client.admin().indices().delete(new DeleteIndexRequest(index)).actionGet();
		}
	}

	public static void indexEntityFacts() throws IOException {
		Application app = new GuiceApplicationBuilder().build();
		IndexComponent index = app.injector().instanceOf(new BindingKey<>(IndexComponent.class));
		for (File file : ENTITYFACTS_FILES) {
			String json = Files.lines(Paths.get(file.toURI())).collect(Collectors.joining());
			index.client()
					.prepareIndex(HomeController.config("index.entityfacts.index"),
							HomeController.config("index.entityfacts.type"))
					.setId(file.getName().split("\\.")[0]).setSource(json, XContentType.JSON).execute().actionGet();
		}
		index.client().admin().indices().refresh(new RefreshRequest()).actionGet();
	}

	public static boolean indexExists(final Client client, final String index) {
		return client.admin().indices().prepareExists(index).execute().actionGet().isExists();
	}

}
