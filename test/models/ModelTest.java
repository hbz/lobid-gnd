package models;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.elasticsearch.common.geo.GeoPoint;
import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;

import apps.Convert;
import play.libs.Json;

public class ModelTest {

	@Test
	public void testModelCreation() {
		String jsonLd = jsonLdFor("4074335-4");
		JsonNode json = Json.parse(jsonLd);
		AuthorityResource res = new AuthorityResource(json);
		Assert.assertNotNull(res.getId());
		Assert.assertNotNull(res.getType());
		Assert.assertEquals(new GeoPoint(51.508530, -0.125740), res.location());
	}

	@Test
	public void testModelCreationPolygonLocation() {
		Assert.assertEquals(new GeoPoint(47.659166, 8.876111),
				new AuthorityResource(Json.parse(jsonLdFor("4057120-8"))).location());
	}

	public static String jsonLdFor(String id) {
		try {
			Model sourceModel = ModelFactory.createDefaultModel();
			String ttl = Files.readAllLines(Paths.get(new File("test/ttl/" + id + ".ttl").toURI())).stream()
					.collect(Collectors.joining("\n"));
			ttl = ttl.replace("https://d-nb.info", "http://d-nb.info");
			sourceModel.read(new BufferedReader(new StringReader(ttl)), null, "TTL");
			return Convert.toJsonLd(id, sourceModel, true, new HashSet<>());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
}
