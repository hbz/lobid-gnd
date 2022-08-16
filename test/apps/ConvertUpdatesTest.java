package apps;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import org.junit.Test;

public class ConvertUpdatesTest {

	@Test
	public void testXmlParsing() throws IOException, FactoryConfigurationError, XMLStreamException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		String xml = "<x><m><l>"
				+ "<RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description><r><d><f1/></d></r><more1/></rdf:Description></RDF>"
				+ "<RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description><r><d><f2/></d></r><more2/></rdf:Description></RDF>"
				+ "</l></m></x>";
		stream.write(xml.getBytes());
		File result = File.createTempFile("parsed", ".rdf");
		result.deleteOnExit();
		ConvertUpdates.writeRdfDescriptions(result, stream);
		System.out.println("Wrote to " + result + ": ");
		Files.readAllLines(Paths.get(result.toURI())).forEach(System.out::println);
	}

}
