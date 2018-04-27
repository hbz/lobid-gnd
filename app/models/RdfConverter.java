/* Copyright 2017 Fabian Steeg, hbz. Licensed under the GPLv2 */

package models;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import com.github.jsonldjava.core.JsonLdError;

import play.Logger;

/**
 * Helper class for converting JsonLd to RDF.
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class RdfConverter {
	/**
	 * RDF serialization formats.
	 */
	@SuppressWarnings("javadoc")
	public static enum RdfFormat {
		RDF_XML("RDF/XML"), //
		N_TRIPLE("N-TRIPLE"), //
		TURTLE("TURTLE");

		private final String name;

		RdfFormat(final String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	/**
	 * @param jsonLd
	 *            The JSON-LD string to convert
	 * @param format
	 *            The RDF format to serialize the jsonLd to
	 * @return The input, converted to the given RDF serialization, or null
	 */
	public static String toRdf(final String jsonLd, final RdfFormat format) {
		try {
			final Model model = ModelFactory.createDefaultModel();
			ByteArrayInputStream in = new ByteArrayInputStream(jsonLd.getBytes(StandardCharsets.UTF_8));
			RDFDataMgr.read(model, in, Lang.JSONLD);
			model.setNsPrefix("bf", "http://id.loc.gov/ontologies/bibframe/");
			model.setNsPrefix("bibo", "http://purl.org/ontology/bibo/");
			model.setNsPrefix("dc", "http://purl.org/dc/elements/1.1/");
			model.setNsPrefix("dcterms", "http://purl.org/dc/terms/");
			model.setNsPrefix("gndo", "http://d-nb.info/standards/elementset/gnd#");
			model.setNsPrefix("lv", "http://purl.org/lobid/lv#");
			model.setNsPrefix("mo", "http://purl.org/ontology/mo/");
			model.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");
			model.setNsPrefix("rdau", "http://rdaregistry.info/Elements/u/");
			model.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
			model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
			model.setNsPrefix("schema", "http://schema.org/");
			model.setNsPrefix("skos", "http://www.w3.org/2004/02/skos/core#");
			model.setNsPrefix("wdrs", "http://www.w3.org/2007/05/powder-s#");
			model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");
			final StringWriter writer = new StringWriter();
			model.write(writer, format.getName());
			return writer.toString();
		} catch (JsonLdError e) {
			Logger.error(e.getMessage(), e);
		}
		return null;
	}
}
