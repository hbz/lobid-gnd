package apps;

import static apps.Convert.config;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

import org.dspace.xoai.model.oaipmh.Header;
import org.dspace.xoai.model.oaipmh.Verb.Type;
import org.dspace.xoai.serviceprovider.client.HttpOAIClient;
import org.dspace.xoai.serviceprovider.exceptions.HttpException;
import org.dspace.xoai.serviceprovider.parameters.Parameters;
import org.elasticsearch.common.io.Streams;

public class Deletions {

	public static void main(String[] args) throws HttpException, IOException, ParseException {
		Parameters params = new Parameters()//
				.withFrom(new SimpleDateFormat("yyyy-MM-dd").parse("2018-11-01"))//
				.withUntil(new SimpleDateFormat("yyyy-MM-dd").parse("2018-11-30"))//
				.withSet("authorities")//
				.withMetadataPrefix("RDFxml")//
				.withVerb(Type.ListRecords);
		InputStream execute = new HttpOAIClient(config("data.updates.url")).execute(params);
		List<String> readAllLines = Streams.readAllLines(execute);
		FileWriter writer = new FileWriter("oai-deletions-test.xml");
		readAllLines.forEach(line -> {
			try {
				writer.write(line);
				writer.write("\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		writer.close();
		// The server would set something like this:
		new Header().withStatus(Header.Status.DELETED);

	}

}
