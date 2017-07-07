package controllers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.google.common.collect.ImmutableMap;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

import apps.Convert;
import play.Environment;
import play.mvc.Controller;
import play.mvc.Result;

/**
 * This controller contains an action to handle HTTP requests to the
 * application's home page.
 */
public class HomeController extends Controller {

	@Inject
	Environment env;

	/**
	 * An action that renders an HTML page with a welcome message. The
	 * configuration in the <code>routes</code> file means that this method will
	 * be called when the application receives a <code>GET</code> request with a
	 * path of <code>/</code>.
	 */
	public Result index() {
		return ok(views.html.index.render(ImmutableMap.of(//
				"London", controllers.routes.HomeController.authority("4074335-4").toString(), //
				"hbz", controllers.routes.HomeController.authority("2047974-8").toString(), //
				"Goethe", controllers.routes.HomeController.authority("118540238").toString())));
	}

	public Result authority(String name) {
		response().setHeader("Access-Control-Allow-Origin", "*");
		String jsonLd = Convert.toJsonLd(name, sourceModel(name), env.isDev());
		return ok(jsonLd).as("application/json; charset=utf-8");
	}

	public Result context() {
		response().setHeader("Access-Control-Allow-Origin", "*");
		try {
			File file = env.getFile("conf/context.jsonld");
			Path path = Paths.get(file.getAbsolutePath());
			return ok(Files.readAllLines(path).stream().collect(Collectors.joining("\n")))
					.as("application/ld+json; charset=utf-8");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private Model sourceModel(String gndId) {
		Model sourceModel = ModelFactory.createDefaultModel();
		String sourceUrl = "http://d-nb.info/gnd/" + gndId + "/about/lds";
		sourceModel.read(sourceUrl);
		return sourceModel;
	}
}
