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

import modules.IndexTest;
import play.mvc.Http;
import play.mvc.Http.Status;
import play.mvc.Result;
import play.test.Helpers;

@RunWith(Parameterized.class)
public class HomeControllerTest extends IndexTest {

	@Parameters(name = "{0} -> {1}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { //
				// index
				{ routes.HomeController.index().toString(), Status.OK }, //
				// search
				{ routes.HomeController.search("*", "", "", 0, 10, "json").toString(), Status.OK },
				{ routes.HomeController.search("*", "", "", 0, 10, "jsonl").toString(), Status.OK },
				{ routes.HomeController.search("*", "", "", 0, 10, "json:suggest").toString(), Status.OK },
				{ routes.HomeController.search("++test", "", "", 0, 10, "html").toString(),
						Status.INTERNAL_SERVER_ERROR },
				{ routes.HomeController.search("*", "", "", 0, 10, "jsonfoo").toString(),
						Status.UNSUPPORTED_MEDIA_TYPE },
				{ routes.HomeController.search("*", "", "", 0, 10, "ttl").toString(), Status.UNSUPPORTED_MEDIA_TYPE },
				{ routes.HomeController.search("*", "", "", 0, 10, "rdf").toString(), Status.UNSUPPORTED_MEDIA_TYPE },
				{ routes.HomeController.search("*", "", "", 0, 10, "nt").toString(), Status.UNSUPPORTED_MEDIA_TYPE },
				// authority
				{ routes.HomeController.authority("4791358-7", "json").toString(), Status.OK },
				{ routes.HomeController.authority("118649019", "html").toString(), Status.OK },
				{ routes.HomeController.authority("abc", "json").toString(), Status.NOT_FOUND },
				{ routes.HomeController.authority("1090750048", "json").toString(), Status.MOVED_PERMANENTLY },
				{ routes.HomeController.authority("4791358-7", "jsonl").toString(), Status.UNSUPPORTED_MEDIA_TYPE },
				{ routes.HomeController.authority("4791358-7", "jsonfoo").toString(), Status.UNSUPPORTED_MEDIA_TYPE },
				{ routes.HomeController.authority("4791358-7", "json:suggest").toString(),
						Status.UNSUPPORTED_MEDIA_TYPE } });
	}

	public HomeControllerTest(String route, int status) {
		this.route = route;
		this.status = status;
	}

	private String route;
	private int status;

	@Test
	public void testIndex() {
		Http.RequestBuilder request = new Http.RequestBuilder().method(GET).uri(route);
		Result result = route(Helpers.fakeApplication(), request);
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
