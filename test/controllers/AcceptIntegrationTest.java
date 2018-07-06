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
			// search, default format: JSON
			{ fakeRequest(GET, "/gnd/search?q=*"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/gnd/search?q=*").header("Accept", "*/*"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/gnd/search?q=*&format="), /*->*/ "application/json" },
			{ fakeRequest(GET, "/gnd/search?q=*&format=json"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/gnd/search?q=*&format=whatever"), /*->*/ "text/plain" },
			{ fakeRequest(GET, "/gnd/search?q=*").header("Accept", "text/plain"), /*->*/ "text/plain" },
			// search, bulk format: JSON lines
			{ fakeRequest(GET, "/gnd/search?q=*").header("Accept", "application/x-jsonlines"), /*->*/ "application/x-jsonlines" },
			{ fakeRequest(GET, "/gnd/search?format=jsonl"), /*->*/ "application/x-jsonlines" },
			{ fakeRequest(GET, "/gnd/search?q=*&format=jsonl"), /*->*/ "application/x-jsonlines" },
			{ fakeRequest(GET, "/gnd/search?q=vwxyz&format=jsonl"), /*->*/ "application/x-jsonlines" },
			// search, other formats as query param:
			{ fakeRequest(GET, "/gnd/search?q=*&format=html"), /*->*/ "text/html" },
			// search, other formats via header:
			{ fakeRequest(GET, "/gnd/search?q=*").header("Accept", "application/json"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/gnd/search?q=*").header("Accept", "text/html"), /*->*/ "text/html" },
			// get, default format: JSON
			{ fakeRequest(GET, "/gnd/118820591"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/gnd/118820591?format="), /*->*/ "application/json" },
			{ fakeRequest(GET, "/gnd/118820591?format=json"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/gnd/118820591?format=whatever"), /*->*/ "text/plain" },
			{ fakeRequest(GET, "/gnd/118820591?format=whatever").header("Accept", "text/html"), /*->*/ "text/plain" },
			{ fakeRequest(GET, "/gnd/118820591").header("Accept", "text/plain"), /*->*/ "application/n-triples" },
			// get, other formats as query param:
			{ fakeRequest(GET, "/gnd/118820591?format=html"), /*->*/ "text/html" },
			{ fakeRequest(GET, "/gnd/118820591?format=rdf"), /*->*/ "application/rdf+xml" },
			{ fakeRequest(GET, "/gnd/118820591?format=ttl"), /*->*/ "text/turtle" },
			{ fakeRequest(GET, "/gnd/118820591?format=nt"), /*->*/ "application/n-triples" },
			// get, other formats as path elem:
			{ fakeRequest(GET, "/gnd/118820591.html"), /*->*/ "text/html" },
			{ fakeRequest(GET, "/gnd/118820591.rdf"), /*->*/ "application/rdf+xml" },
			{ fakeRequest(GET, "/gnd/118820591.ttl"), /*->*/ "text/turtle" },
			{ fakeRequest(GET, "/gnd/118820591.nt"), /*->*/ "application/n-triples" },
			// get, others formats via header:
			{ fakeRequest(GET, "/gnd/118820591").header("Accept", "application/json"), /*->*/ "application/json" },
			{ fakeRequest(GET, "/gnd/118820591").header("Accept", "text/html"), /*->*/ "text/html" },
			{ fakeRequest(GET, "/gnd/118820591").header("Accept", "text/xml"), /*->*/ "application/rdf+xml" },
			{ fakeRequest(GET, "/gnd/118820591").header("Accept", "application/xml"), /*->*/ "application/rdf+xml" },
			{ fakeRequest(GET, "/gnd/118820591").header("Accept", "application/rdf+xml"), /*->*/ "application/rdf+xml" },
			{ fakeRequest(GET, "/gnd/118820591").header("Accept", "text/turtle"), /*->*/ "text/turtle" },
			{ fakeRequest(GET, "/gnd/118820591").header("Accept", "application/x-turtle"), /*->*/ "text/turtle" },
			{ fakeRequest(GET, "/gnd/118820591").header("Accept", "text/plain"), /*->*/ "application/n-triples" },
			{ fakeRequest(GET, "/gnd/118820591").header("Accept", "application/n-triples"), /*->*/ "application/n-triples" }});
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