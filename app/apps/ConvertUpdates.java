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

public class ConvertUpdates {

	public static void main(String[] args) throws IOException {
		if (args.length == 1) {
			Pair<String, String> startAndEnd = getUpdates(args[0]);
			backup(new File(config("data.updates.rdf")), startAndEnd.getLeft(), startAndEnd.getRight());
			ConvertBaseline.main(new String[] { config("data.updates.rdf"), config("data.updates.data"),
					config("index.delete.updates") });
			backup(new File(config("data.updates.data")), startAndEnd.getLeft(), startAndEnd.getRight());
		} else {
			System.err.println("Pass one argument: get updates since a given date in ISO format, e.g. 2019-06-13");
		}
	}

	private static Pair<String, String> getUpdates(String startOfUpdates) {
		int intervalSize = Convert.CONFIG.getInt("data.updates.interval");
		String start = startOfUpdates;
		String end = addDays(start, intervalSize);
		int intervals = calculateIntervals(startOfUpdates, intervalSize);
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
			start = addDays(start, intervalSize);
			if (i == intervals - 2)
				end = getToday();
			else
				end = addDays(end, intervalSize);
		}
		try (FileWriter writer = new FileWriter(file, true)) {
			writer.write("</RDF>");
		} catch (IOException e) {
			e.printStackTrace();
		}
		writeLastSuccessfulUpdate(end);
		return Pair.of(startOfUpdates, end);
	}

	public static void process(final String baseUrl, String from, String until, File result)
			throws NoSuchFieldException, IOException, ParserConfigurationException, SAXException, TransformerException {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
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

	private static String addDays(String start, int intervalSize) {
		LocalDate endDate = LocalDate.parse(start).plusDays(intervalSize);
		return LocalDate.parse(getToday()).isBefore(endDate) ? getToday() : endDate.format(DateTimeFormatter.ISO_DATE);
	}

	private static String getToday() {
		return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE);
	}

	private static int calculateIntervals(String startOfUpdates, int intervalSize) {
		String end = getToday();
		final LocalDate startDate = LocalDate.parse(startOfUpdates);
		final LocalDate endDate = LocalDate.parse(end);
		long timeSpan = startDate.until(endDate, ChronoUnit.DAYS);
		return ((int) timeSpan / intervalSize) + 1;
	}

}
