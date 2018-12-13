/* Copyright 2017-2018 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package modules;

import static controllers.HomeController.CONFIG;
import static controllers.HomeController.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Scanner;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.BoostingQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import controllers.HomeController;
import play.Logger;
import play.inject.ApplicationLifecycle;
import play.mvc.Http.Status;

public interface IndexComponent {
	Client client();

	SearchResponse query(String q, String filter, int from, int size);

	QueryStringQueryBuilder queryStringQuery(String q);

	public default SearchResponse query(String q) {
		return query(q, "", 0, 10);
	}

	void startup();

}

@Singleton
class ElasticsearchServer implements IndexComponent {

	private static final Settings SETTINGS = Settings.builder()
			.put("cluster.name", HomeController.config("index.cluster")).build();

	private final TransportClient client;

	@Inject
	public ElasticsearchServer(ApplicationLifecycle lifecycle) {
		client = new PreBuiltTransportClient(SETTINGS);
		CONFIG.getStringList("index.hosts").forEach((host) -> {
			try {
				client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), 9300));
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		});
		startup();
		lifecycle.addStopHook(() -> {
			client.close();
			return null;
		});
	}

	@Override
	public Client client() {
		return client;
	}

	public void startup() {
		client.admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
		client.admin().indices().refresh(new RefreshRequest()).actionGet();
		String pathToJson = config("data.jsonlines");
		String pathToUpdates = config("data.updates.data");
		String indexName = config("index.name");
		try {
			if (!indexExists(client, indexName)) {
				createEmptyIndex(client, indexName, config("index.settings"));
				if (new File(pathToJson).exists()) {
					indexData(client, pathToJson, indexName);
				}
			} else {
				Logger.info("Index {} exists. Delete index or change index name in config to reindex from {}",
						indexName, pathToJson);
			}
			if (new File(pathToUpdates).exists()) {
				Logger.info("Indexing updates from " + pathToUpdates);
				indexData(client, pathToUpdates, indexName);
			}
			deleteDeprecatedResources();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Logger.info("Using Elasticsearch index settings: {}", SETTINGS.getAsMap());
	}

	private void deleteDeprecatedResources() throws IOException {
		File file = new File(config("index.delete"));
		Logger.info("Deleting entities listed in {}", file);
		if (file.exists()) {
			try (Scanner s = new Scanner(new FileInputStream(file))) {
				while (s.hasNextLine()) {
					String id = s.nextLine();
					DeleteResponse response = client.prepareDelete(config("index.name"), config("index.type"), id)
							.execute().actionGet();
					Logger.debug("Delete {}: status {}: {}", id, response.status(), response);
					if (response.status().getStatus() == Status.OK) {
						Logger.info("Deleted {}", id);
					}
				}
			}
			client.admin().indices().refresh(new RefreshRequest()).actionGet();
		}
	}

	static boolean indexExists(final Client client, final String index) {
		return client.admin().indices().prepareExists(index).execute().actionGet().isExists();
	}

	static void createEmptyIndex(final Client client, final String index, final String mappings) throws IOException {
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

	static BulkRequestBuilder bulkRequest = null;

	static void indexData(final Client client, final String path, final String index) throws IOException {
		// Set number_of_replicas to 0 for faster indexing. See:
		// https://www.elastic.co/guide/en/elasticsearch/reference/master/tune-for-indexing-speed.html
		updateSettings(client, index, Settings.builder().put("index.number_of_replicas", 0));
		for (String p : new File(path).list(new SuffixFileFilter("jsonl"))) {
			try (BufferedReader br = new BufferedReader(
					new InputStreamReader(new FileInputStream(new File(path, p)), StandardCharsets.UTF_8))) {
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

	@Override
	public SearchResponse query(String q, String filter, int from, int size) {
		QueryStringQueryBuilder positive = queryStringQuery(q).field("_all").field("preferredName.ngrams")
				.field("variantName.ngrams").field("preferredName", 2f).field("variantName", 1f)
				.field("gndIdentifier", 2f);
		MatchQueryBuilder negative = QueryBuilders.matchQuery("type", "UndifferentiatedPerson");
		BoostingQueryBuilder boostQuery = QueryBuilders.boostingQuery(positive, negative).negativeBoost(0.1f);
		BoolQueryBuilder query = QueryBuilders.boolQuery().must(boostQuery);
		if (!filter.isEmpty()) {
			query = query.filter(queryStringQuery(filter));
		}
		SearchRequestBuilder requestBuilder = client().prepareSearch(config("index.name")).setQuery(query).setFrom(from)
				.setSize(size);
		for (String a : HomeController.AGGREGATIONS) {
			requestBuilder.addAggregation(AggregationBuilders.terms(a).field(a).size(1000));
		}
		Logger.debug("Search request: {}", requestBuilder);
		SearchResponse response = requestBuilder.get();
		return response;
	}

	@Override
	public QueryStringQueryBuilder queryStringQuery(String q) {
		// Clean up single forward slash, but keep regular /expressions/
		q = q.indexOf('/') == q.lastIndexOf('/') ? q.replace("/", " ") : q;
		return QueryBuilders.queryStringQuery(q).defaultOperator(Operator.AND);
	}
}