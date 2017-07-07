package apps;

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

public class Index {

	private static class ConfigurableNode extends Node {
		public ConfigurableNode(Settings settings, Collection<Class<? extends Plugin>> classpathPlugins) {
			super(InternalSettingsPreparer.prepareEnvironment(settings, null), Version.CURRENT, classpathPlugins);
		}
	}

	private static Settings clientSettings = Settings.settingsBuilder().put("path.home", ".").put("http.port", "7111")
			.put("transport.tcp.port", "7122").put("script.default_lang", "native").build();

	private static Node node = new ConfigurableNode(
			nodeBuilder().settings(clientSettings).local(true).getSettings().build(),
			Arrays.asList(/* BundlePlugin.class */)).start();

	public static final Client CLIENT = node.client();

	private static final String INDEX_NAME = "authorities";

	private static final String INDEX_TYPE = "authority";

	public static void main(String[] args) throws IOException {
		String pathToJson = "GND.jsonl";
		if (!indexExists(CLIENT, INDEX_NAME)) {
			createEmptyIndex(CLIENT, INDEX_NAME, "conf/index-settings.json");
			indexData(CLIENT, pathToJson, INDEX_NAME);
		} else {
			System.out.println("Index exists. Delete the data/ directory to reindex from " + pathToJson);
		}
	}

	static void createEmptyIndex(final Client aClient, final String aIndexName, final String aPathToIndexSettings)
			throws IOException {
		deleteIndex(aClient, aIndexName);
		CreateIndexRequestBuilder cirb = aClient.admin().indices().prepareCreate(aIndexName);
		if (aPathToIndexSettings != null) {
			String settingsMappings = Files.lines(Paths.get(aPathToIndexSettings)).collect(Collectors.joining());
			cirb.setSource(settingsMappings);
		}
		cirb.execute().actionGet();
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
			} else {
				data = line;
				bulkRequest.add(client.prepareIndex(aIndex, INDEX_TYPE, id).setSource(data));
			}
			currentLine++;
			pendingIndexRequests++;
			if (pendingIndexRequests == 1000) {
				executeBulk();
				bulkRequest = client.prepareBulk();
				pendingIndexRequests = 0;
			}
		}
	}

	private static void executeBulk() {
		BulkResponse bulkResponse = bulkRequest.execute().actionGet();
		if (bulkResponse.hasFailures()) {
			bulkResponse.forEach(item -> {
				if (item.isFailed()) {
					Logger.error("Indexing {} failed: {}", item.getId(), item.getFailureMessage());
				}
			});
		} else {
			Logger.info("Indexed 1000, took: " + bulkResponse.getTook());
		}
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

}
