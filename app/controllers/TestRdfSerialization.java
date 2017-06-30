package controllers;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.jena.JenaRDFParser;
import com.github.jsonldjava.utils.JsonUtils;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

public class TestRdfSerialization {

	public static void main(String[] args) throws JsonLdError, JsonGenerationException, IOException {
		Model model = ModelFactory.createDefaultModel();
		model.read("http://d-nb.info/gnd/2047974-8/about/lds");
		Object jsonLd = JsonLdProcessor.fromRDF(model, new JenaRDFParser());
		String context = "http://hub.culturegraph.org/entityfacts/context/v1/entityfacts.jsonld";
		JsonLdOptions options = new JsonLdOptions();
		options.setCompactArrays(false);
		jsonLd = JsonLdProcessor.compact(jsonLd, context, options);
		System.out.println(JsonUtils.toPrettyString(jsonLd));
	}

}
