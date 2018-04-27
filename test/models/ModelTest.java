package models;

import java.io.FileNotFoundException;
import java.io.FileReader;

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
	public void testModelCreation() throws FileNotFoundException {
		String jsonLd = jsonLdFor("4074335-4");
		JsonNode json = Json.parse(jsonLd);
		AuthorityResource res = Json.fromJson(json, AuthorityResource.class);
		Assert.assertNotNull(res.getId());
		Assert.assertNotNull(res.getType());
		Assert.assertEquals(new GeoPoint(51.508530, -0.125740), res.location());
	}

	private String jsonLdFor(String id) throws FileNotFoundException {
		Model sourceModel = ModelFactory.createDefaultModel();
		sourceModel.read(new FileReader("test/ttl/" + id + ".ttl"), null, "TTL");
		return Convert.toJsonLd(id, sourceModel, true);
	}
}
