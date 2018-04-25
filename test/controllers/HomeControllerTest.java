package controllers;

import static controllers.HomeController.formatCount;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static play.test.Helpers.GET;
import static play.test.Helpers.route;

import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import play.Application;
import play.inject.guice.GuiceApplicationBuilder;
import play.mvc.Http;
import play.mvc.Http.Status;
import play.mvc.Result;
import play.test.WithApplication;

@RunWith(Parameterized.class)
public class HomeControllerTest extends WithApplication {

	@Parameters(name = "{0} -> {1}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { //
				{ routes.HomeController.index().toString(), Status.OK }, //
				{ routes.HomeController.search("*", "", 0, 10, "json").toString(), Status.OK },
				{ routes.HomeController.authority("---", "json").toString(), Status.NOT_FOUND } });
	}

	public HomeControllerTest(String route, int status) {
		this.route = route;
		this.status = status;
	}

	private String route;
	private int status;

	@Override
	protected Application provideApplication() {
		return new GuiceApplicationBuilder().build();
	}

	@Test
	public void testIndex() {
		Http.RequestBuilder request = new Http.RequestBuilder().method(GET).uri(route);
		Result result = route(app, request);
		assertEquals(status, result.status());
	}

	@Test
	public void testFormatCount() {
		assertThat(formatCount(100), equalTo("100"));
		assertThat(formatCount(1000), equalTo("1.000"));
		assertThat(formatCount(10000), equalTo("10.000"));
		assertThat(formatCount(100000), equalTo("100.000"));
		assertThat(formatCount(1000000), equalTo("1.000.000"));
	}

}
