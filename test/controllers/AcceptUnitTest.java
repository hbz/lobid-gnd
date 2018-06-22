/* Copyright 2014-2018, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static play.test.Helpers.fakeRequest;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import play.api.http.MediaRange;
import play.mvc.Http.RequestBuilder;

/**
 * Unit tests for functionality provided by the {@link Accept} class.
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class AcceptUnitTest {

	// test data parameters, formatted as "input /*->*/ expected output"
	@Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] {
				// neither supported header nor supported format given, return
				// default:
				{ fakeRequest(), null, /*->*/ "json" }, //
				{ fakeRequest(), "", /*->*/ "json" }, //
				{ fakeRequest(), "xml", /*->*/ "json" }, //
				{ fakeRequest().header("Accept", ""), null, /*->*/ "json" }, //
				{ fakeRequest().header("Accept", "application/pdf"), null, /*->*/ "json" },
				// no header, just format parameter:
				{ fakeRequest(), "html", /*->*/ "html" }, //
				{ fakeRequest(), "json", /*->*/ "json" }, //
				{ fakeRequest(), "json:preferredName", /*->*/ "json(.+)?" }, //
				{ fakeRequest(), "ttl", /*->*/ "ttl" }, //
				{ fakeRequest(), "nt", /*->*/ "nt" }, //
				{ fakeRequest(), "jsonl", /*->*/ "jsonl" },
				// supported content types, no format parameter given:
				{ fakeRequest().header("Accept", "text/html"), null, /*->*/ "html" },
				{ fakeRequest().header("Accept", "application/json"), null, /*->*/ "json" },
				{ fakeRequest().header("Accept", "application/ld+json"), null, /*->*/ "json" },
				{ fakeRequest().header("Accept", "text/plain"), null, /*->*/ "nt" },
				{ fakeRequest().header("Accept", "application/n-triples"), null, /*->*/ "nt" },
				{ fakeRequest().header("Accept", "text/turtle"), null, /*->*/ "ttl" },
				{ fakeRequest().header("Accept", "application/x-turtle"), null, /*->*/ "ttl" },
				{ fakeRequest().header("Accept", "application/xml"), null, /*->*/ "rdf" },
				{ fakeRequest().header("Accept", "application/rdf+xml"), null, /*->*/ "rdf" },
				{ fakeRequest().header("Accept", "text/xml"), null, /*->*/ "rdf" },
				{ fakeRequest().header("Accept", "application/x-jsonlines"), null, /*->*/ "jsonl" },
				// we pick the preferred content type:
				{ fakeRequest().header("Accept", "text/html,application/json"), null, /*->*/"html" },
				{ fakeRequest().header("Accept", "application/json,text/html"), null, /*->*/ "json" },
				// format parameter overrides header:
				{ fakeRequest().header("Accept", "text/html"), "json", /*->*/ "json" }, //
				{ fakeRequest().header("Accept", "text/html"), "jsonl", /*->*/ "jsonl" } });
	}

	private RequestBuilder fakeRequest;
	private String passedFormat;
	private String expectedFormat;

	public AcceptUnitTest(RequestBuilder request, String givenFormat, String expectedFormat) {
		this.fakeRequest = request;
		this.passedFormat = givenFormat;
		this.expectedFormat = expectedFormat;
	}

	@Test
	public void test() {
		List<MediaRange> acceptedTypes = fakeRequest.build().acceptedTypes();
		String description = String.format("resulting format for passedFormat=%s, acceptedTypes=%s", passedFormat,
				acceptedTypes);
		assertThat(description, Accept.formatFor(passedFormat, acceptedTypes).queryParamString,
				startsWith(expectedFormat));
	}

}