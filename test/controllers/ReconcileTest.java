/* Copyright 2014-2018, hbz. Licensed under the Eclipse Public License 1.0 */

package controllers;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static play.test.Helpers.GET;
import static play.test.Helpers.POST;
import static play.test.Helpers.contentAsString;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.route;
import static play.test.Helpers.running;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import modules.IndexTest;
import play.Application;
import play.Logger;
import play.libs.Json;
import play.mvc.Result;

@SuppressWarnings("javadoc")
public class ReconcileTest extends IndexTest {

	@Test
	public void reconcileMetadataRequestNoCallback() {
		Application application = fakeApplication();
		running(application, () -> {
			Result result = route(application, fakeRequest(GET, "/gnd/reconcile"));
			assertNotNull(result);
			assertThat(result.contentType().get(), is(equalTo("application/json")));
			assertNotNull(Json.parse(contentAsString(result)));
			assertThat(contentAsString(result), containsString("https:"));
			assertThat(contentAsString(result), not(containsString("http:")));
			assertThat(result.header("Access-Control-Allow-Origin").get(), is(equalTo("*")));
		});
	}

	@Test
	public void reconcilePropertiesRequest() {
		Application application = fakeApplication();
		running(application, () -> {
			Result result = route(application, fakeRequest(GET, "/gnd/reconcile/properties"));
			assertNotNull(result);
			assertThat(result.contentType().get(), is(equalTo("application/json")));
			assertThat(result.header("Access-Control-Allow-Origin").get(), is(equalTo("*")));
		});
	}

	@Test
	public void reconcileMetadataRequestWithCallback() {
		Application application = fakeApplication();
		running(application, () -> {
			Result result = route(application, fakeRequest(GET, "/gnd/reconcile?callback=jsonp"));
			assertNotNull(result);
			assertThat(result.contentType().get(), is(equalTo("application/json")));
			assertThat(contentAsString(result), startsWith("/**/jsonp("));
		});
	}

	@Test
	// curl --data 'queries={"q99":{"query":"*"}}' localhost:9000/gnd/reconcile
	public void reconcileRequest() {
		Application application = fakeApplication();
		running(application, () -> {
			Result result = route(application, fakeRequest(POST, "/gnd/reconcile")
					.bodyForm(ImmutableMap.of("queries", "{\"q99\":{\"query\":\"Twain, Mark\"}}")));
			String content = contentAsString(result);
			Logger.debug(Json.prettyPrint(Json.parse(content)));
			assertThat(content, containsString("q99"));
			assertThat(content, containsString("\"match\":false"));
			assertThat(content, containsString("\"match\":true"));
			assertThat(result.header("Access-Control-Allow-Origin").get(), is(equalTo("*")));
		});
	}

	@Test
	public void reconcileRequestWithType() {
		Application application = fakeApplication();
		running(application, () -> {
			Result result = route(application, fakeRequest(POST, "/gnd/reconcile").bodyForm(
					ImmutableMap.of("queries", "{\"q99\":{\"query\":\"Twain, Mark\", \"type\":\"CorporateBody\"}}")));
			String content = contentAsString(result);
			Logger.debug(Json.prettyPrint(Json.parse(content)));
			assertThat(content, containsString("q99"));
			assertFalse(Json.parse(content).findValue("result").elements().hasNext());
		});
	}

}