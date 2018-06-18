/* Copyright 2017 Fabian Steeg, hbz. Licensed under the GPLv2 */

package models;

import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertThat;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import apps.Convert;
import models.RdfConverter.RdfFormat;

/**
 * See http://www.playframework.com/documentation/2.3.x/JavaFunctionalTest
 */
@SuppressWarnings("javadoc")
@RunWith(Parameterized.class)
public class RdfConverterTests {

	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(RdfFormat.values()).stream().map(format -> new Object[] { format })
				.collect(Collectors.toList());
	}

	private RdfFormat format;

	public RdfConverterTests(RdfFormat format) {
		this.format = format;
	}

	@Test
	public void testJsonldToRdf() throws FileNotFoundException {
		String jsonLd = jsonLdFor("4074335-4");
		assertThat(jsonLd, notNullValue());
		String rdf = RdfConverter.toRdf(jsonLd, format);
		assertThat(rdf, notNullValue());
	}

	private String jsonLdFor(String id) throws FileNotFoundException {
		Model sourceModel = ModelFactory.createDefaultModel();
		sourceModel.read(new FileReader("test/ttl/" + id + ".ttl"), null, "TTL");
		return Convert.toJsonLd(id, sourceModel, false, new HashSet<>());
	}
}
