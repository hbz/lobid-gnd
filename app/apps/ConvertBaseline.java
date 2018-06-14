package apps;

import static apps.Convert.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;

import org.culturegraph.mf.elasticsearch.JsonToElasticsearchBulk;
import org.culturegraph.mf.io.FileOpener;
import org.culturegraph.mf.io.ObjectWriter;
import org.culturegraph.mf.xml.XmlDecoder;
import org.culturegraph.mf.xml.XmlElementSplitter;

import apps.Convert.ToAuthorityJson;
import controllers.HomeController;

public class ConvertBaseline {

	public static void main(String[] args) {
		if (args.length == 2 || args.length == 0) {
			String input = args.length == 2 ? args[0] : config("data.rdfxml");
			String output = args.length == 2 ? args[1] : config("data.jsonlines");
			FileOpener opener = new FileOpener();
			XmlElementSplitter splitter = new XmlElementSplitter();
			splitter.setElementName("Description");
			splitter.setTopLevelElement("rdf:RDF");
			ToAuthorityJson encodeJson = new ToAuthorityJson();
			JsonToElasticsearchBulk bulk = new JsonToElasticsearchBulk("id", config("index.type"),
					config("index.name"));
			new File(config("index.delete")).delete();
			final ObjectWriter<String> writer = new ObjectWriter<>(output);
			opener//
					.setReceiver(new XmlDecoder())//
					.setReceiver(splitter)//
					.setReceiver(encodeJson)//
					.setReceiver(bulk)//
					.setReceiver(writer);
			opener.process(input);
			opener.closeStream();
			try (PrintWriter pw = new PrintWriter(new FileOutputStream(HomeController.config("index.delete"), true))) {
				encodeJson.deprecated.forEach(id -> pw.println(id));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		} else {
			System.err.println("Pass either two arguments, the input and the output file, "
					+ "or none, for input and output files specified in application.conf");
		}
	}
}
