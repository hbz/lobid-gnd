/* Copyright 2018, hbz. Licensed under the Eclipse Public License 1.0 */

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

import org.junit.Test;

import modules.IndexTest;
import play.Application;
import play.libs.Json;
import play.mvc.Result;

@SuppressWarnings("javadoc")
public class JsonResponseTest extends IndexTest {

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
}