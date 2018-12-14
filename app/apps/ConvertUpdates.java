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
		if (args.length == 1 || args.length == 2) {
			Pair<String, String> startAndEnd = getUpdates(args[0], args.length == 2 ? args[1] : null);
			String start = startAndEnd.getLeft();
			String end = startAndEnd.getRight();
			backup(new File(config("data.updates.rdf")), new File(config("data.rdfxml")), start, end);
			ConvertBaseline.main(new String[] { config("data.updates.rdf"), config("data.updates.data") });
			backup(new File(config("data.updates.data")), new File(config("data.jsonlines")), start, end);
		} else {
			System.err.println(
					"Pass either one argument, the start date for getting updates, or two, the start and the end date.");
		}
	}

	private static Pair<String, String> getUpdates(String startOfUpdates, String endOfUpdates) {
		int intervalSize = Convert.CONFIG.getInt("data.updates.interval");
		String start = startOfUpdates;
		String end = endOfUpdates == null ? addDays(start, intervalSize) : endOfUpdates;
		int intervals = calculateIntervals(startOfUpdates, intervalSize);
		File file = new File(config("data.updates.rdf"));
		try (FileWriter writer = new FileWriter(file, false)) {
			writer.write("<rdf:RDF " + "xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" "//
					+ "xmlns:bibo=\"http://purl.org/ontology/bibo/\" "//
					+ "xmlns:dbp=\"http://dbpedia.org/property/\" "//
					+ "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "//
					+ "xmlns:dcmitype=\"http://purl.org/dc/dcmitype/\" "//
					+ "xmlns:dcterms=\"http://purl.org/dc/terms/\" "//
					+ "xmlns:dnb_intern=\"http://dnb.de/\" "//
					+ "xmlns:dnba=\"http://d-nb.info/standards/elementset/agrelon#\" "//
					+ "xmlns:dnbt=\"http://d-nb.info/standards/elementset/dnb#\" "//
					+ "xmlns:ebu=\"http://www.ebu.ch/metadata/ontologies/ebucore/ebucore#\" "//
					+ "xmlns:editeur=\"https://ns.editeur.org/thema/\" "//
					+ "xmlns:foaf=\"http://xmlns.com/foaf/0.1/\" "//
					+ "xmlns:gbv=\"http://purl.org/ontology/gbv/\" "//
					+ "xmlns:geo=\"http://www.opengis.net/ont/geosparql#\" "//
					+ "xmlns:gndo=\"http://d-nb.info/standards/elementset/gnd#\" "//
					+ "xmlns:isbd=\"http://iflastandards.info/ns/isbd/elements/\" "//
					+ "xmlns:lib=\"http://purl.org/library/\" "//
					+ "xmlns:marcRole=\"http://id.loc.gov/vocabulary/relators/\" "//
					+ "xmlns:mo=\"http://purl.org/ontology/mo/\" "//
					+ "xmlns:owl=\"http://www.w3.org/2002/07/owl#\" "//
					+ "xmlns:rdau=\"http://rdaregistry.info/Elements/u/\" "//
					+ "xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\" "//
					+ "xmlns:schema=\"http://schema.org/\" "//
					+ "xmlns:sf=\"http://www.opengis.net/ont/sf#\" "//
					+ "xmlns:skos=\"http://www.w3.org/2004/02/skos/core#\" "//
					+ "xmlns:umbel=\"http://umbel.org/umbel#\" "//
					+ "xmlns:v=\"http://www.w3.org/2006/vcard/ns#\" "//
					+ "xmlns:wdrs=\"http://www.w3.org/2007/05/powder-s#\" "//
					+ "xmlns:vivo=\"http://vivoweb.org/ontology/core#\">");
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
			writer.write("</rdf:RDF>");
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

	private static void backup(File source, File folder, String start, String end) throws IOException {
		String name = source.getName();
		File target = new File(folder, String.format("%s_%s_%s%s", name.substring(0, name.lastIndexOf('.')), start, end,
				name.substring(name.lastIndexOf('.'))));
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
