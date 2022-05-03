/* Copyright 2017-2018 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package modules;

import static controllers.HomeController.CONFIG;
import static controllers.HomeController.config;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.BoostingQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import controllers.HomeController;
import play.Logger;
import play.inject.ApplicationLifecycle;

public interface IndexComponent {
	Client client();

	SearchResponse query(String q, String filter, String sort, int from, int size);

	SearchResponse query(QueryBuilder q, String filter, QueryBuilder optional, String sort, int from, int size);

	QueryStringQueryBuilder queryStringQuery(String q);

	public default SearchResponse query(String q) {
		return query(q, "", "", 0, 10);
	}

}

@Singleton
class ElasticsearchServer implements IndexComponent {

	private static final Settings SETTINGS = Settings.builder()
			.put("cluster.name", HomeController.config("index.prod.cluster")).build();

	private final TransportClient client;

	@Inject
	public ElasticsearchServer(ApplicationLifecycle lifecycle) {
		client = new PreBuiltTransportClient(SETTINGS);
		CONFIG.getStringList("index.prod.hosts").forEach((host) -> {
			try {
				client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), 9300));
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		});
		lifecycle.addStopHook(() -> {
			client.close();
			return null;
		});
	}

	@Override
	public Client client() {
		return client;
	}

	@Override
	public SearchResponse query(String q, String filter, String sort, int from, int size) {
		return runQuery(filter, null, sort, from, size, mainQuery(q));
	}

	@Override
	public SearchResponse query(QueryBuilder q, String filter, QueryBuilder optional, String sort, int from, int size) {
		return runQuery(filter, optional, sort, from, size, q);
	}

	private QueryBuilder mainQuery(String q) {
		return queryStringQuery(q).field("_all").field("preferredName.ngrams").field("variantName.ngrams")
				.field("preferredName", 2f).field("preferredName.ascii", 2f).field("variantName", 1f)
				.field("variantName.ascii", 1f).field("gndIdentifier", 2f);
	}

	private SearchResponse runQuery(String filter, QueryBuilder optional, String sort, int from, int size,
			QueryBuilder positive) {
		MatchQueryBuilder negative = QueryBuilders.matchQuery("type", "UndifferentiatedPerson");
		BoostingQueryBuilder boostQuery = QueryBuilders.boostingQuery(positive, negative).negativeBoost(0.1f);
		BoolQueryBuilder query = QueryBuilders.boolQuery().must(boostQuery);
		if (!filter.isEmpty()) {
			query = query.filter(queryStringQuery(filter));
		}
		if (optional != null) {
			query = query.should(optional);
		}
		SearchRequestBuilder requestBuilder = client().prepareSearch(config("index.prod.name")).setQuery(query)
				.setFrom(from).setSize(size);
		if (!sort.isEmpty()) {
			String[] fieldAndOrder = sort.split(":");
			FieldSortBuilder fieldSort = SortBuilders.fieldSort(fieldAndOrder[0]);
			requestBuilder.addSort(
					fieldAndOrder.length == 2 ? fieldSort.order(SortOrder.fromString(fieldAndOrder[1])) : fieldSort);
		}
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