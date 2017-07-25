package apps;

import static apps.Convert.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import org.culturegraph.mf.elasticsearch.JsonToElasticsearchBulk;
import org.culturegraph.mf.io.ObjectWriter;
import org.culturegraph.mf.xml.XmlDecoder;
import org.culturegraph.mf.xml.XmlElementSplitter;

import apps.Convert.OpenOaiPmh;
import apps.Convert.ToAuthorityJson;

public class ConvertUpdates {

	public static void main(String[] args) {
		if (args.length == 1 || args.length == 2) {
			ArrayList<OpenOaiPmh> buildUpdatePipes = buildUpdatePipes(args[0], args.length == 2 ? args[1] : null);
			buildUpdatePipes.forEach(p -> {
				try {
					p.process(config("data.updates.url"));
				} catch (Exception x) {
					x.printStackTrace();
				} finally {
					p.closeStream();
				}
			});
		} else {
			System.err.println(
					"Pass either one argument, the date for getting updates, two, the start and the end date.");
		}
	}

	private static ArrayList<OpenOaiPmh> buildUpdatePipes(String startOfUpdates, String endOfUpdates) {
		int intervalSize = 50;
		String start = startOfUpdates;
		String end = endOfUpdates == null ? addDays(start, intervalSize) : endOfUpdates;
		final ArrayList<OpenOaiPmh> updateOpenerList = new ArrayList<>();
		int intervals = calculateIntervals(startOfUpdates, intervalSize);
		for (int i = 0; i < intervals; i++) {
			OpenOaiPmh opener = new OpenOaiPmh(start, end);
			XmlElementSplitter splitter = new XmlElementSplitter();
			splitter.setElementName("Description");
			splitter.setTopLevelElement("rdf:RDF");
			String output = config("data.updates.data");
			opener//
					.setReceiver(new XmlDecoder())//
					.setReceiver(splitter)//
					// .setReceiver(new ToString())
					.setReceiver(new ToAuthorityJson())//
					.setReceiver(new JsonToElasticsearchBulk("id", config("index.type"), config("index.name")))//
					.setReceiver(new ObjectWriter<String>(output));
			updateOpenerList.add(opener);
			start = addDays(start, intervalSize);
			if (i == intervals - 2)
				end = getToday();
			else
				end = addDays(end, intervalSize);
		}
		return updateOpenerList;
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
