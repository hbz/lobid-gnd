package apps;

import static apps.Convert.config;
import static controllers.HomeController.CONFIG;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Assert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import controllers.HomeController;
import modules.IndexComponent;
import play.Application;
import play.Logger;
import play.api.inject.BindingKey;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http.Status;

public class Index {

	public static IndexComponent index = new GuiceApplicationBuilder().build().injector()
			.instanceOf(new BindingKey<>(IndexComponent.class));

	public static void main(String[] args) {
		if (args.length == 1 && (args[0].equals("baseline") || args[0].equals("updates"))) {
			if (args[0].equals("baseline")) {
				index(indexName, client, config("data.jsonlines"), config("index.delete.baseline"));
			} else {
				index(indexName, client, config("data.updates.data"), config("index.delete.updates"));
			}
		} else {
			System.err.println("Pass one argument, 'baseline' or 'updates'. See config/application.conf.");
		}
		client.close();
		// Why is this required? Also needs 'trapExit := false' in build.sbt
		System.exit(0);
	}

	static String indexName = HomeController.config("index.name.prod");

	static Client client = index.client();

	static {
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
		client.admin().indices().refresh(new RefreshRequest()).actionGet();
	}

	protected static final File[] ENTITYFACTS_FILES = new File("test/entityfacts").listFiles();

	public static void deleteIndex(final String index) {
		Application app = new GuiceApplicationBuilder().build();
		IndexComponent indexComponent = app.injector().instanceOf(new BindingKey<>(IndexComponent.class));
		Client client = indexComponent.client();
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

	public static void createEmptyIndex(final Client client, final String index, final String mappings)
			throws IOException {
		CreateIndexRequestBuilder cirb = client.admin().indices().prepareCreate(index);
		cirb.setSettings(Settings.builder()
				// bulk indexing only
				.put("index.refresh_interval", "-1")
				// 1 shard per node
				.put("index.number_of_shards", CONFIG.getStringList("index.hosts").size()));
		if (mappings != null) {
			cirb.setSource(Files.lines(Paths.get(mappings)).collect(Collectors.joining()), XContentType.JSON);
		}
		cirb.execute().actionGet();
		client.admin().indices().refresh(new RefreshRequest()).actionGet();
	}

	public static IndexComponent indexBaselineAndUpdates() {
		index(indexName, client, config("data.jsonlines"), config("index.delete.baseline"));
		index(indexName, client, config("data.updates.data"), config("index.delete.updates"));
		return index;
	}

	static void index(String indexName, Client client, String pathToJson, String pathToDeprecated) {
		indexName = indexName == null ? config("index.name.prod") : indexName;
		try {
			if (!Index.indexExists(client, indexName)) {
				Logger.info("Creating new empty index {}", indexName);
				Index.createEmptyIndex(client, indexName, config("index.settings"));
			} else {
				Logger.info("Index {} exists. Delete index or change index name in config to create new index",
						indexName);
			}
			if (new File(pathToJson).exists()) {
				Logger.info("Indexing {} into index {}", pathToJson, indexName);
				Index.indexData(client, pathToJson, indexName);
			}
			deleteDeprecatedResources(client, pathToDeprecated);
		} catch (IOException e) {
			e.printStackTrace();
		}
		Logger.info("Indexing done");
	}

	public static void indexData(final Client client, final String path, final String index) throws IOException {
		// Set number_of_replicas to 0 for faster indexing. See:
		// https://www.elastic.co/guide/en/elasticsearch/reference/master/tune-for-indexing-speed.html
		updateSettings(client, index, Settings.builder().put("index.number_of_replicas", 0));
		File file = new File(path);
		FileFilter fileFilter = new SuffixFileFilter("jsonl");
		for (File f : file.isDirectory() ? file.listFiles(fileFilter) : new File[] { file }) {
			try (BufferedReader br = new BufferedReader(
					new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
				bulkIndex(br, client, index);
			}
		}
		updateSettings(client, index, Settings.builder().put("index.number_of_replicas", 1));
		client.admin().indices().refresh(new RefreshRequest()).actionGet();
	}

	private static void updateSettings(final Client client, final String index, Builder settings) {
		UpdateSettingsResponse response = client.admin().indices().prepareUpdateSettings(index).setSettings(settings)
				.get();
		if (!response.isAcknowledged()) {
			Logger.error("Not acknowledged: Update index settings {}: {}", settings, response);
		}
	}

	static BulkRequestBuilder bulkRequest = null;

	private static void bulkIndex(final BufferedReader br, final Client client, final String indexName)
			throws IOException {
		final ObjectMapper mapper = new ObjectMapper();
		String line;
		int currentLine = 1;
		String data = null;
		String[] idUriParts = null;
		String id = null;

		bulkRequest = client.prepareBulk();
		int pendingIndexRequests = 0;

		// First line: index with id, second line: source
		while ((line = br.readLine()) != null) {
			JsonNode rootNode = mapper.readValue(line, JsonNode.class);
			if (currentLine % 2 != 0) {
				JsonNode index = rootNode.get("index");
				idUriParts = index.findValue("_id").asText().split("/");
				id = idUriParts[idUriParts.length - 1].replace("#!", "");
				pendingIndexRequests++;
			} else {
				Form nfc = Normalizer.Form.NFC;
				data = Normalizer.isNormalized(line, nfc) ? line : Normalizer.normalize(line, nfc);
				bulkRequest.add(
						client.prepareIndex(indexName, config("index.type"), id).setSource(data, XContentType.JSON));
			}
			currentLine++;
			if (pendingIndexRequests == 1000) {
				executeBulk(pendingIndexRequests);
				bulkRequest = client.prepareBulk();
				pendingIndexRequests = 0;
			}
		}
		executeBulk(pendingIndexRequests);
	}

	private static void executeBulk(int pendingIndexRequests) {
		if (pendingIndexRequests == 0) {
			return;
		}
		BulkResponse bulkResponse = bulkRequest.execute().actionGet();
		if (bulkResponse.hasFailures()) {
			bulkResponse.forEach(item -> {
				if (item.isFailed()) {
					Logger.error("Indexing {} failed: {}", item.getId(), item.getFailureMessage());
				}
			});
		}
		Logger.info("Indexed {} docs, took: {}", pendingIndexRequests, bulkResponse.getTook());
	}

	private static void deleteDeprecatedResources(Client client, String delete) throws IOException {
		File file = new File(delete);
		Logger.info("Deleting entities listed in {}", file);
		if (file.exists()) {
			try (Scanner s = new Scanner(new FileInputStream(file))) {
				while (s.hasNextLine()) {
					String id = s.nextLine();
					DeleteResponse response = client.prepareDelete(config("index.name.prod"), config("index.type"), id)
							.execute().actionGet();
					Logger.debug("Delete {}: status {}: {}", id, response.status(), response);
					if (response.status().getStatus() == Status.OK) {
						Logger.info("Deleted {}", id);
					}
				}
			}
			client.admin().indices().refresh(new RefreshRequest()).actionGet();
			Logger.info("Delete and refresh done");
		}
	}

}
