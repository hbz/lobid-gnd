/* Copyright 2017-2023 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package apps;

import static apps.Convert.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathException;

import org.apache.commons.lang3.tuple.Pair;
import org.oclc.oai.harvester2.app.RawWrite;
import org.xml.sax.SAXException;

import helper.Email;

/**
 * Configurable to-the-minute. Start time is read from file which was saved last time of successfully updating.
 * Default end time is now, like eg. 2023-11-06T15:59Z .
 */
public class ConvertUpdates {

	static private final short MAX_TRIES = 2;
	static private final int WAIT_PER_RETRY = 14400000; // ms => 4h
	static private final int DAY_IN_MINUTES = 1440;
	static private final String FAIL_MESSAGE = "Tried to get the update several times, but the data remains to be empty."
			+ "This may or may not be a problem on the side of the data provider.";
	static private boolean rawDates = false;
	/* OAI-PMH expects this format */
	static final DateTimeFormatter dateTimeFormatter =
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length >= 1) {
			ZonedDateTime endOfUpdates = args.length >= 2 ? ZonedDateTime.parse(args[1]) : null;
			if (args.length == 3) {
				rawDates = args[2].equals("--raw-dates");
			}
			Pair<ZonedDateTime, ZonedDateTime> startAndEnd = getUpdatesAndConvert(ZonedDateTime.parse(args[0]), endOfUpdates);
			File dataUpdate = new File(config("data.updates.data"));
			short tried = 1;
			while (dataUpdate.length() == 0 ) {
				tried++;
				System.err.println("Tried " + tried + " times to get the data, but it's empty.");
				if (MAX_TRIES <= tried)
					break;
				System.err.println("Going to retry in " + WAIT_PER_RETRY / 1000 / 60 + " min");
				Thread.sleep(WAIT_PER_RETRY);
				startAndEnd = getUpdatesAndConvert(startAndEnd.getLeft(), endOfUpdates);
			}
			if (dataUpdate.length() == 0) {
				System.err.println("Tried " + tried
						+ " times to get the data, but it remains empty. This may or may not be a problem on the side of the data provider. Going to send a mail...");
				Email.sendEmail(config("mail.sender"), config("mail.recipient"), "GND updates fails :(", FAIL_MESSAGE);
			} else {
				System.err.println("Success getting the update, tried " + tried + " times");
				writeLastSuccessfulUpdate(startAndEnd.getRight());
			}
			backup(dataUpdate, startAndEnd.getLeft(), startAndEnd.getRight());
		} else {
			System.err.println(
					"Argument missing to get updates since (and optionally until) a given date in ISO format, e.g. 2019-06-13");
		}
	}

	private static Pair<ZonedDateTime, ZonedDateTime> getUpdatesAndConvert(final ZonedDateTime startOfUpdates, final ZonedDateTime endOfUpdates) throws IOException {
		Pair<ZonedDateTime, ZonedDateTime> startAndEnd = getUpdates(startOfUpdates, endOfUpdates);
		backup(new File(config("data.updates.rdf")), startAndEnd.getLeft(), startAndEnd.getRight());
		ConvertBaseline.main(new String[] { config("data.updates.rdf"), config("data.updates.data"),
				config("index.delete.updates") });
		return startAndEnd;
	}
	
	private static Pair<ZonedDateTime, ZonedDateTime> getUpdates(final ZonedDateTime startOfUpdates, final ZonedDateTime endOfUpdates) {
		final int intervalInDaysSize = Convert.CONFIG.getInt("data.updates.interval");
		ZonedDateTime start = startOfUpdates;
		final ZonedDateTime givenEndOrNow = endOfUpdates != null ? endOfUpdates
				 : ZonedDateTime.now();
		ZonedDateTime end = ZonedDateTime.from(givenEndOrNow);
		int intervalInDays = rawDates ? 1
				: calculateIntervals(startOfUpdates, givenEndOrNow, intervalInDaysSize);
		File file = new File(config("data.updates.rdf"));
		try (FileWriter writer = new FileWriter(file, false)) {
			writer.write("<RDF>");
		} catch (IOException e) {
			e.printStackTrace();
		}
		String dataUpdateUrl = config("data.updates.url");
		System.out.printf("Trying to get data using %s from %s until %s using a %s days interval , i.e. doing %s lookup(s)\n",
				dataUpdateUrl, start.format(dateTimeFormatter), givenEndOrNow.format(dateTimeFormatter), intervalInDaysSize, intervalInDays);
		for (int i = 0; i < intervalInDays; i++) {
			if (i == intervalInDays - 1)
				end = ZonedDateTime.from(givenEndOrNow);
			else
				end = rawDates ? end
						: ZonedDateTime.from(addMinutes(start, givenEndOrNow,
						(intervalInDaysSize) * DAY_IN_MINUTES /* 'until' is inclusive */));
			try {
				process(dataUpdateUrl, start, end, file);
			} catch (Throwable t) {
				t.printStackTrace();
			}
			start = ZonedDateTime.from(end);
		}
		try (FileWriter writer = new FileWriter(file, true)) {
			writer.write("</RDF>");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Pair.of(startOfUpdates, end);
	}

	public static void process(final String baseUrl, ZonedDateTime from, ZonedDateTime until, File result)
			throws NoSuchFieldException, IOException, ParserConfigurationException, SAXException, TransformerException,
			XMLStreamException, XPathException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		String fromFormatted = from.format(dateTimeFormatter);
		String untilFormatted = until.format(dateTimeFormatter);

		System.out.printf("Calling OAI-PMH at %s from %s until %s\n", baseUrl, fromFormatted, untilFormatted);
		RawWrite.run(baseUrl, fromFormatted, untilFormatted, "RDFxml", "authorities", stream);
		writeRdfDescriptions(result, stream);
	}

	static void writeRdfDescriptions(File result, ByteArrayOutputStream stream)
			throws FactoryConfigurationError, XMLStreamException, IOException {
		try (FileWriter fileWriter = new FileWriter(result, true)) {
			String rdfTag = "RDF";
			String entityTag = "Description";
			XMLOutputFactory outputFactory = XMLOutputFactory.newInstance();
			XMLEventFactory eventFactory = XMLEventFactory.newInstance();
			XMLEventWriter eventWriter = null;
			InputStreamReader inputStreamReader = new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()),
					StandardCharsets.UTF_8);
			XMLEventReader eventReader = XMLInputFactory.newInstance().createXMLEventReader(inputStreamReader);
			Iterator<?> namespaces = null;
			while (eventReader.hasNext()) {
				try {
					XMLEvent nextEvent = eventReader.nextEvent();
					if (nextEvent.isStartElement()) {
						StartElement startElement = nextEvent.asStartElement();
						if (startElement.getName().getLocalPart().equals(rdfTag)) {
							namespaces = startElement.getNamespaces();
							eventWriter = outputFactory.createXMLEventWriter(fileWriter);
							continue;
						} else if (startElement.getName().getLocalPart().equals(entityTag)) {
							QName descriptionName = new QName(startElement.getName().getNamespaceURI(),
									startElement.getName().getLocalPart(), startElement.getName().getPrefix());
							StartElement descriptionWithNamespaces = eventFactory.createStartElement(descriptionName,
									startElement.getAttributes(), namespaces);
							nextEvent = descriptionWithNamespaces;
						}
					} else if (nextEvent.isEndElement()
							&& ((EndElement) nextEvent).getName().getLocalPart().equals(rdfTag)) {
						eventWriter.close();
						eventWriter = null;
					}
					if (eventWriter != null) {
						eventWriter.add(nextEvent);
					}
				} catch (XMLStreamException e) {
					System.err.printf("XMLStreamException, skipping XMLEvent. \n");
					e.printStackTrace();
					eventReader.next();
				}
			}
		}
	}

	private static void writeLastSuccessfulUpdate(ZonedDateTime until) {
		File file = new File(config("data.updates.last"));
		file.delete();
		try (FileWriter writer = new FileWriter(file)) {
			writer.append(until.format(dateTimeFormatter));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void backup(File source, ZonedDateTime start, ZonedDateTime end) throws IOException {
		String name = source.getName();
		File target = new File(new File(config("data.backup")), String.format("%s_%s_%s%s",
				name.substring(0, name.lastIndexOf('.')), start.format(dateTimeFormatter),
				end.format(dateTimeFormatter), name.substring(name.lastIndexOf('.'))));
		Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	private static ZonedDateTime addMinutes(ZonedDateTime start, ZonedDateTime end, int intervalSize) {
		ZonedDateTime endDate = start.plusMinutes(intervalSize); //TODO
		return end.
				isBefore(endDate) ? end : endDate;
	}

	private static int calculateIntervals(ZonedDateTime startOfUpdates, ZonedDateTime endOfUpdates, int intervalSize) {
		long timeSpan = startOfUpdates.until(endOfUpdates, ChronoUnit.MINUTES);
		return ((int) timeSpan / intervalSize / DAY_IN_MINUTES ) + 1;
	}

}
