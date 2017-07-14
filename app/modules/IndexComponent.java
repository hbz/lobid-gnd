package modules;

import static controllers.HomeController.config;
import static org.elasticsearch.node.NodeBuilder.nodeBuilder;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.elasticsearch.Version;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.internal.InternalSettingsPreparer;
import org.elasticsearch.plugins.Plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import play.Logger;
import play.inject.ApplicationLifecycle;

public interface IndexComponent {
	Client client();
}

class EmbeddedIndex implements IndexComponent {

	private static final String INDEX_TYPE = "authority";

	private static class ConfigurableNode extends Node {
		public ConfigurableNode(Settings settings, Collection<Class<? extends Plugin>> classpathPlugins) {
			super(InternalSettingsPreparer.prepareEnvironment(settings, null), Version.CURRENT, classpathPlugins);
		}
	}

	private Settings clientSettings = Settings.settingsBuilder().put("path.home", config("index.home"))
			.put("http.port", config("index.port.http")).put("transport.tcp.port", config("index.port.tcp"))
			.put("script.default_lang", "native").build();

	private Node node = new ConfigurableNode(nodeBuilder().settings(clientSettings).local(true).getSettings().build(),
			Arrays.asList(/* BundlePlugin.class */)).start();

	public final Client client = node.client();

	@Inject
	public EmbeddedIndex(ApplicationLifecycle lifecycle) {
		startup();
		lifecycle.addStopHook(() -> {
			node.close();
			client.close();
			return null;
		});
	}

	@Override
	public Client client() {
		return client;
	}

	private void startup() {
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
		client.admin().indices().refresh(new RefreshRequest()).actionGet();
		String pathToJson = config("data.jsonlines");
		String indexName = config("index.name");
		if (!indexExists(client, indexName)) {
			try {
				createEmptyIndex(client, indexName, config("index.settings"));
				indexData(client, pathToJson, indexName);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			Logger.info("Index exists. Delete the 'data/' directory to reindexfrom " + pathToJson);
		}
		Logger.info("Using Elasticsearch index settings: {}", clientSettings.getAsMap());
	}

	private static void deleteIndex(final Client client, final String index) {
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
		if (indexExists(client, index)) {
			client.admin().indices().delete(new DeleteIndexRequest(index)).actionGet();
		}
	}

	private static boolean indexExists(final Client client, final String index) {
		return client.admin().indices().prepareExists(index).execute().actionGet().isExists();
	}

	static void createEmptyIndex(final Client aClient, final String aIndexName, final String aPathToIndexSettings)
			throws IOException {
		deleteIndex(aClient, aIndexName);
		CreateIndexRequestBuilder cirb = aClient.admin().indices().prepareCreate(aIndexName);
		if (aPathToIndexSettings != null) {
			String settingsMappings = Files.lines(Paths.get(aPathToIndexSettings)).collect(Collectors.joining());
			cirb.setSource(settingsMappings);
		}
		cirb.execute().actionGet();//
		aClient.admin().indices().refresh(new RefreshRequest()).actionGet();
	}

	static BulkRequestBuilder bulkRequest = null;

	static void indexData(final Client aClient, final String aPath, final String aIndex) throws IOException {
		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(aPath), StandardCharsets.UTF_8))) {
			readData(br, aClient, aIndex);
		}
		aClient.admin().indices().refresh(new RefreshRequest()).actionGet();
	}

	private static void readData(final BufferedReader br, final Client client, final String aIndex) throws IOException {
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
				data = line;
				bulkRequest.add(client.prepareIndex(aIndex, INDEX_TYPE, id).setSource(data));
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
}