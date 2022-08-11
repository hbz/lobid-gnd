/* Copyright 2017-2018 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.lang3.tuple.Pair;
import org.joox.JOOX;
import org.joox.Match;
import org.xml.sax.SAXException;

import ORG.oclc.oai.harvester2.app.RawWrite;
import helper.Email;

public class ConvertUpdates {

	static private final short MAX_TRIES = 2;
	static private final int WAIT_PER_RETRY = 14400000; // ms => 4h
	static private final String FAIL_MESSAGE = "Tried to get the update several times, but the data remains to be empty."
			+ "This may or may not be a problem on the side of the data provider.";

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length == 1 || args.length == 2) {
			String endOfUpdates = args.length == 2 ? args[1] : null;
			Pair<String, String> startAndEnd = getUpdatesAndConvert(args[0], endOfUpdates);
			File dataUpdate = new File(config("data.updates.data"));
			short tried = 0;
			while (dataUpdate.length() == 0 ) {
				tried++;
				System.err.println("Tried " + tried
						+ " times to get the data, but it's empty.");
				if (MAX_TRIES <= tried)
					break;
				System.err.println("Going to retry in " + WAIT_PER_RETRY / 1000 / 60 + " min");
				Thread.sleep(WAIT_PER_RETRY);
				startAndEnd = getUpdatesAndConvert(args[0], endOfUpdates);
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

	private static Pair<String, String> getUpdatesAndConvert(final String startOfUpdates, final String endOfUpdates) throws IOException {
		Pair<String, String> startAndEnd = getUpdates(startOfUpdates, endOfUpdates);
		backup(new File(config("data.updates.rdf")), startAndEnd.getLeft(), startAndEnd.getRight());
		ConvertBaseline.main(new String[] { config("data.updates.rdf"), config("data.updates.data"),
				config("index.delete.updates") });
		return startAndEnd;
	}
	
	private static Pair<String, String> getUpdates(String startOfUpdates, String endOfUpdates) {
		int intervalSize = Convert.CONFIG.getInt("data.updates.interval");
		String start = startOfUpdates;
		String givenEndOrToday = endOfUpdates != null ? endOfUpdates
				: LocalDateTime.now().format(DateTimeFormatter.ISO_DATE);
		String end = addDays(start, givenEndOrToday, intervalSize);
		int intervals = calculateIntervals(startOfUpdates, givenEndOrToday,
				intervalSize);
		File file = new File(config("data.updates.rdf"));
		try (FileWriter writer = new FileWriter(file, false)) {
			writer.write("<RDF>");
		} catch (IOException e) {
			e.printStackTrace();
		}
		for (int i = 0; i < intervals; i++) {
			try {
				process(config("data.updates.url"), start, end, file);
			} catch (Throwable t) {
				t.printStackTrace();
			}
			start = addDays(start, givenEndOrToday, intervalSize);
			if (i == intervals - 2)
				end = givenEndOrToday;
			else
				end = addDays(end, givenEndOrToday, intervalSize);
		}
		try (FileWriter writer = new FileWriter(file, true)) {
			writer.write("</RDF>");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return Pair.of(startOfUpdates, end);
	}

	public static void process(final String baseUrl, String from, String until, File result)
			throws NoSuchFieldException, IOException, ParserConfigurationException, SAXException, TransformerException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		System.out.printf("Calling OAI-PMH at %s from %s until %s\n", baseUrl, from, until);
		RawWrite.run(baseUrl, from, until, "RDFxml", "authorities", stream);
		Match m = JOOX.$(new InputStreamReader(new ByteArrayInputStream(stream.toByteArray()), StandardCharsets.UTF_8));
		m.find("Description").write(new FileWriter(result, true));
	}

	private static void writeLastSuccessfulUpdate(String until) {
		File file = new File(config("data.updates.last"));
		file.delete();
		try (FileWriter writer = new FileWriter(file)) {
			writer.append(until);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void backup(File source, String start, String end) throws IOException {
		String name = source.getName();
		File target = new File(new File(config("data.backup")), String.format("%s_%s_%s%s",
				name.substring(0, name.lastIndexOf('.')), start, end, name.substring(name.lastIndexOf('.'))));
		Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}

	private static String addDays(String start, String end, int intervalSize) {
		LocalDate endDate = LocalDate.parse(start).plusDays(intervalSize);
		return LocalDate.parse(end).isBefore(endDate) ? end : endDate.format(DateTimeFormatter.ISO_DATE);
	}

	private static int calculateIntervals(String startOfUpdates, String endOfUpdates, int intervalSize) {
		final LocalDate startDate = LocalDate.parse(startOfUpdates);
		final LocalDate endDate = LocalDate.parse(endOfUpdates);
		long timeSpan = startDate.until(endDate, ChronoUnit.DAYS);
		return ((int) timeSpan / intervalSize) + 1;
	}

}
