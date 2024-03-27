/* Copyright 2018, 2022 hbz. Licensed under the Eclipse Public License 1.0 */

package controllers;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static play.test.Helpers.GET;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.route;
import static play.test.Helpers.running;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.databind.JsonNode;

import modules.IndexTest;
import play.Application;
import play.libs.Json;
import play.mvc.Result;

@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class JsonResponseTest extends IndexTest {

	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { { "/search?format=json" }, { "/2136169-1" } });
	}

	private String path;

	public JsonResponseTest(String path) {
		this.path = path;
	}

	@Test
	public void jsonRequestNoInternalUrl() {
		Application application = fakeApplication();
		running(application, () -> {
			Result result = route(application, fakeRequest(GET, path));
			assertNotNull(result);
			assertThat(result.contentType().get(), is(equalTo("application/json")));
			String content = contentAsString(result);
			assertNotNull(content);
			assertThat(content, not(containsString("localhost")));
			JsonNode json = Json.parse(content);
			assertNotNull(json);
			assertThat(content, equalTo(Json.prettyPrint(json)));
		});
	}
}