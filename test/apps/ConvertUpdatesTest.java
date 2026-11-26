package apps;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLStreamException;

import org.junit.Test;
import org.w3c.dom.NodeList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConvertUpdatesTest {

	@Test
	public void testNoRecordsMatchIsNotAnError() throws Exception {
		NodeList noRecordsMatch = parseOaiErrors("<error code=\"noRecordsMatch\"/>");
		NodeList otherError = parseOaiErrors("<error code=\"badArgument\"/>");
		NodeList noErrors = parseOaiErrors("");

		assertTrue(ConvertUpdates.isNoRecordsMatch(noRecordsMatch));
		assertFalse(ConvertUpdates.isNoRecordsMatch(noErrors));
		try {
			ConvertUpdates.isNoRecordsMatch(otherError);
			fail("Expected other OAI-PMH errors to fail");
		} catch (IOException e) {
			assertTrue(e.getMessage().contains("badArgument"));
		}
	}

	@Test
	public void testWriteSkippedUpdate() throws IOException {
		File skippedUpdates = File.createTempFile("skipped-updates", ".txt");
		skippedUpdates.deleteOnExit();
		ZonedDateTime from = ZonedDateTime.parse("2022-06-22T11:08:23Z");
		ZonedDateTime until = ZonedDateTime.parse("2022-06-23T18:08:23Z");

		ConvertUpdates.writeSkippedUpdate(skippedUpdates, from, until);
		ConvertUpdates.writeSkippedUpdate(skippedUpdates, until, until.plusDays(1));

		assertEquals(Arrays.asList("2022-06-22T11:08:23Z 2022-06-23T18:08:23Z",
				"2022-06-23T18:08:23Z 2022-06-24T18:08:23Z"), Files.readAllLines(skippedUpdates.toPath()));
	}

	private NodeList parseOaiErrors(String errors) throws Exception {
		String response = "<OAI-PMH xmlns=\"http://www.openarchives.org/OAI/2.0/\">"
				+ "<ListRecords>" + errors + "</ListRecords></OAI-PMH>";
		ByteArrayInputStream stream = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(stream)
				.getElementsByTagNameNS("http://www.openarchives.org/OAI/2.0/", "error");
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
