/* Copyright 2017-2018 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package controllers;

import java.util.Collection;

import play.api.http.MediaRange;

/**
 * Helper class for dealing with content negotiation for the `Accept` header.
 * 
 * @author Fabian Steeg (fsteeg)
 *
 */
public class Accept {

	private Accept() {
		// static helper class, don't instantiate
	}

	enum Format {
		JSON_LD("json(:.+)?", "application/json", "application/ld+json"), //
		JSON_LINES("jsonl", "application/x-jsonlines"), //
		HTML("html", "text/html"), //
		PREVIEW("preview", "text/html"), //
		RDF_XML("rdf", "application/rdf+xml", "application/xml", "text/xml"), //
		N_TRIPLE("nt", "application/n-triples", "text/plain"), //
		TURTLE("ttl", "text/turtle", "application/x-turtle");

		String[] types;
		String queryParamString;

		private Format(String format, String... types) {
			this.queryParamString = format;
			this.types = types;
		}
	}

	/**
	 * @param formatParam
	 *            The requested format parameter
	 * @param acceptedTypes
	 *            The accepted types
	 * @return The selected format for the given parameter and types
	 */
	public static Format formatFor(String formatParam, Collection<MediaRange> acceptedTypes) {
		for (Format format : Format.values())
			if (formatParam != null && formatParam.matches(format.queryParamString))
				return format;
		if (formatParam == null || formatParam.isEmpty())
			for (MediaRange mediaRange : acceptedTypes)
				for (Format format : Format.values())
					for (String mimeType : format.types)
						if (mediaRange.accepts(mimeType))
							return format;
		return (formatParam == null || formatParam.isEmpty()) && acceptedTypes.isEmpty() ? Format.JSON_LD : null;
	}

}
