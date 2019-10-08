/* Copyright 2017-2018 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package apps;

import static apps.Convert.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.culturegraph.mf.elasticsearch.JsonToElasticsearchBulk;
import org.culturegraph.mf.io.FileOpener;
import org.culturegraph.mf.io.ObjectWriter;
import org.culturegraph.mf.xml.XmlDecoder;
import org.culturegraph.mf.xml.XmlElementSplitter;

import apps.Convert.ToAuthorityJson;

public class ConvertBaseline {

	public static void main(String[] args) {
		if (args.length == 3 || args.length == 0) {
			File inFile = new File(args.length == 3 ? args[0] : config("data.rdfxml"));
			List<String> input = inFile.isDirectory()
					? Arrays.asList(inFile.listFiles()).stream().map(File::getAbsolutePath).collect(Collectors.toList())
					: Arrays.asList(inFile.getAbsolutePath());
			File outFile = new File(args.length == 3 ? args[1] : config("data.jsonlines"));
			XmlElementSplitter splitter = new XmlElementSplitter();
			splitter.setElementName("Description");
			splitter.setTopLevelElement("rdf:RDF");
			ToAuthorityJson encodeJson = new ToAuthorityJson();
			JsonToElasticsearchBulk bulk = new JsonToElasticsearchBulk("id", config("index.type"),
					config("index.prod.name"));
			File deprecatedFile = new File(args.length == 3 ? args[2] : config("index.delete.baseline"));
			deprecatedFile.delete();
			for (String file : input) {
				FileOpener opener = new FileOpener();
				File out = outFile.isDirectory() ? new File(outFile, new File(file).getName() + ".jsonl") : outFile;
				final ObjectWriter<String> writer = new ObjectWriter<>(out.getAbsolutePath());
				opener//
						.setReceiver(new XmlDecoder())//
						.setReceiver(splitter)//
						.setReceiver(encodeJson)//
						.setReceiver(bulk)//
						.setReceiver(writer);
				opener.process(file);
				opener.closeStream();
				writer.closeStream();
			}
			try (PrintWriter pw = new PrintWriter(new FileOutputStream(deprecatedFile, true))) {
				encodeJson.deprecated.forEach(id -> pw.println(id));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		} else {
			System.err.println(
					"Pass either three arguments, the input (file or directory), the output file, and the file to store deprecated IDs,"
							+ "or none, for input, output, and deprecated locations specified in application.conf");
		}
	}
}
