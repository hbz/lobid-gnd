/* Copyright 2018-2019, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static play.test.Helpers.GET;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.route;
import static play.test.Helpers.running;

import java.util.Optional;

import org.junit.Test;

import play.Application;
import play.libs.Json;
import play.mvc.Result;

@SuppressWarnings("javadoc")
public class JsonResponseTest {

	@Test
	public void jsonRequestNoInternalUrl() {
		Application application = fakeApplication();
		running(application, () -> {
			Result result = route(application, fakeRequest(GET, "/gnd/search?format=json"));
			assertNotNull(result);
			assertThat(result.contentType().get(), is(equalTo("application/json")));
			assertNotNull(Json.parse(contentAsString(result)));
			assertThat(contentAsString(result), not(containsString("localhost")));
		});
	}

	@Test
	public void jsonRequestResponseHeader() {
		Application application = fakeApplication();
		running(application, () -> {
			String id = "100002617";
			Result result = route(application, fakeRequest(GET, "/gnd/" + id));
			Optional<String> header = result.header("Link");
			assertTrue(header.isPresent());
			String value = header.get();
			assertThat(value, containsString(id));
			assertThat(value,
					containsString(String.format("<https://test.skohub.io/inbox?target=http://test.lobid.org/gnd/%s>; "
							+ "rel=\"http://www.w3.org/ns/ldp#inbox\"", id)));
			assertThat(value, containsString("<https://test.skohub.io/hub>; rel=\"hub\""));
			assertThat(value, containsString(String.format("<http://test.lobid.org/gnd/%s>; rel=\"self\"", id)));
		});
	}

}