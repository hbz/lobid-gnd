package apps;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import org.junit.Test;

public class ConvertUpdatesTest {

	@Test
	public void testWriteSkippedUpdate() throws IOException {
		File skippedUpdates = File.createTempFile("skipped-updates", ".txt");
		skippedUpdates.deleteOnExit();
		ZonedDateTime from = ZonedDateTime.parse("2022-06-22T11:08:23Z");
		ZonedDateTime until = ZonedDateTime.parse("2022-06-23T18:08:23Z");

		ConvertUpdates.writeSkippedUpdate(skippedUpdates, from, until);
		ConvertUpdates.writeSkippedUpdate(skippedUpdates, until, until.plusDays(1));

		org.junit.Assert.assertEquals(java.util.Arrays.asList("2022-06-22T11:08:23Z 2022-06-23T18:08:23Z",
				"2022-06-23T18:08:23Z 2022-06-24T18:08:23Z"), Files.readAllLines(skippedUpdates.toPath()));
	}

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
