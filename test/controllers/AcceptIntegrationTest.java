/* Copyright 2014-2018, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static play.test.Helpers.GET;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.route;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import modules.IndexTest;
import play.mvc.Http.RequestBuilder;
import play.mvc.Result;

/**
 * Integration tests for functionality provided by the {@link Accept} class.
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class AcceptIntegrationTest extends IndexTest {

	// test data parameters, formatted as "input /*->*/ expected output"
	@Parameters
	public static Collection<Object[]> data() {
		// @formatter:off
		return Arrays.asList(new Object[][] {
			// service meta data: context and dataset description
			{ fakeRequest(GET, "/context.jsonld"), /*->*/ "application/ld+json" },
			{ fakeRequest(GET, "/dataset.jsonld"), /*->*/ "application/ld+json" },
			{ fakeRequest(GET, "/dataset"), /*->*/ "application/ld+json" },
			{ fakeRequest(GET, "/dataset").header("Accept", "text/html"), /*->*/ "text/html" },
			{ fakeRequest(GET, "/dataset").header("Accept", "application/ld+json"), /*->*/ "application/ld+json" },
			{ fakeRequest(GET, "/dataset").header("Accept", "application/json"), /*->*/ "application/ld+json" },
			// search, default format: JSON
			{ fakeRequest(GET, "/search?q=*"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/search?q=*").header("Accept", "*/*"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/search?q=*&format="), /*->*/ "application/json" },
			{ fakeRequest(GET, "/search?q=*&format=json"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/search?q=*&format=whatever"), /*->*/ "text/html" },
			{ fakeRequest(GET, "/search?q=*").header("Accept", "text/plain"), /*->*/ "text/html" },
			// search, bulk format: JSON lines
			{ fakeRequest(GET, "/search?q=*").header("Accept", "application/x-jsonlines"), /*->*/ "application/x-jsonlines" },
			{ fakeRequest(GET, "/search?format=jsonl"), /*->*/ "application/x-jsonlines" },
			{ fakeRequest(GET, "/search?q=*&format=jsonl"), /*->*/ "application/x-jsonlines" },
			{ fakeRequest(GET, "/search?q=vwxyz&format=jsonl"), /*->*/ "application/x-jsonlines" },
			// search, other formats as query param:
			{ fakeRequest(GET, "/search?q=*&format=html"), /*->*/ "text/html" },
			// search, other formats via header:
			{ fakeRequest(GET, "/search?q=*").header("Accept", "application/json"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/search?q=*").header("Accept", "text/html"), /*->*/ "text/html" },
			// get, default format: JSON
			{ fakeRequest(GET, "/118820591"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/118820591?format="), /*->*/ "application/json" },
			{ fakeRequest(GET, "/118820591?format=json"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/118820591?format=whatever"), /*->*/ "text/html" },
			{ fakeRequest(GET, "/118820591?format=whatever").header("Accept", "text/html"), /*->*/ "text/html" },
			{ fakeRequest(GET, "/118820591").header("Accept", "text/plain"), /*->*/ "application/n-triples" },
			// get, other formats as query param:
			{ fakeRequest(GET, "/118820591?format=html"), /*->*/ "text/html" },
			{ fakeRequest(GET, "/118820591?format=rdf"), /*->*/ "application/rdf+xml" },
			{ fakeRequest(GET, "/118820591?format=ttl"), /*->*/ "text/turtle" },
			{ fakeRequest(GET, "/118820591?format=nt"), /*->*/ "application/n-triples" },
			{ fakeRequest(GET, "/118820591?format=preview"), /*->*/ "text/html" },
			// get, other formats as path elem:
			{ fakeRequest(GET, "/118820591.html"), /*->*/ "text/html" },
			{ fakeRequest(GET, "/118820591.rdf"), /*->*/ "application/rdf+xml" },
			{ fakeRequest(GET, "/118820591.ttl"), /*->*/ "text/turtle" },
			{ fakeRequest(GET, "/118820591.nt"), /*->*/ "application/n-triples" },
			{ fakeRequest(GET, "/118820591.preview"), /*->*/ "text/html" },
			// get, others formats via header:
			{ fakeRequest(GET, "/118820591").header("Accept", "application/json"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/118820591").header("Accept", "text/html"), /*->*/ "text/html" },
			{ fakeRequest(GET, "/118820591").header("Accept", "text/xml"), /*->*/ "application/rdf+xml" },
			{ fakeRequest(GET, "/118820591").header("Accept", "application/xml"), /*->*/ "application/rdf+xml" },
			{ fakeRequest(GET, "/118820591").header("Accept", "application/rdf+xml"), /*->*/ "application/rdf+xml" },
			{ fakeRequest(GET, "/118820591").header("Accept", "text/turtle"), /*->*/ "text/turtle" },
			{ fakeRequest(GET, "/118820591").header("Accept", "application/x-turtle"), /*->*/ "text/turtle" },
			{ fakeRequest(GET, "/118820591").header("Accept", "text/plain"), /*->*/ "application/n-triples" },
			{ fakeRequest(GET, "/118820591").header("Accept", "application/n-triples"), /*->*/ "application/n-triples" }});
	} // @formatter:on

	private RequestBuilder fakeRequest;
	private String contentType;

	public AcceptIntegrationTest(RequestBuilder request, String contentType) {
		this.fakeRequest = request;
		this.contentType = contentType;
	}

	@Test
	public void test() {
		Result result = route(fakeApplication(), fakeRequest);
		assertNotNull(result);
		assertThat(result.contentType().get(), is(equalTo(contentType)));
	}

}