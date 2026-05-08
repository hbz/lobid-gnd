package apps;

import static apps.Convert.config;
import static models.ModelTest.jsonLdFor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.google.common.collect.ImmutableMap;

import controllers.HomeController;
import modules.IndexComponent;
import play.Application;
import play.api.inject.BindingKey;
import play.inject.guice.GuiceApplicationBuilder;
import play.libs.Json;

@RunWith(Parameterized.class)
public class ConvertTest {

	@Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][] { //
				{ "test/data/GND.rdf", "test/data/GND.jsonl" }, //
				{ "test/data/input", "test/data/index" } });
	}

	private String input;
	private String index;

	public ConvertTest(String input, String index) {
		this.input = input;
		this.index = index;
	}

	@BeforeClass
	public static void setUpBootstrappingIndex() throws IOException {
		Application app = new GuiceApplicationBuilder().build();
		IndexComponent indexComponent = app.injector().instanceOf(new BindingKey<>(IndexComponent.class));
		Client client = indexComponent.client();
		String bootstrappingIndexName = HomeController.config("index.boot.name");
		Index.createEmptyIndex(client, bootstrappingIndexName, HomeController.config("index.settings"));
		Index.indexData(client, "test/data/index", bootstrappingIndexName);
	}

	@AfterClass
	public static void tearDownBootstrappingIndex() {
		Index.deleteIndex(HomeController.config("index.boot.name"));
	}

	@Test
	public void testGeneralNames() throws FileNotFoundException {
		String jsonLd = jsonLdFor("100002617");
		// Replace literal fields with general fields:
		assertFalse(jsonLd.contains("preferredNameForThePerson"));
		assertFalse(jsonLd.contains("variantNameForThePerson"));
		assertTrue(jsonLd.contains("preferredName"));
		assertTrue(jsonLd.contains("variantName"));
		// Don't replace entity fields:
		assertTrue(jsonLd.contains("preferredNameEntityForThePerson"));
		assertTrue(jsonLd.contains("variantNameEntityForThePerson"));
	}

	@Test
	public void testPreferredNameIsTextual() throws FileNotFoundException {
		String jsonLd = jsonLdFor("100002617");
		JsonNode jsonNode = Json.parse(jsonLd);
		assertTrue(jsonNode.get("preferredName").isTextual());
	}

	@Test
	public void testgndIdentifierIsTextual() throws FileNotFoundException {
		String jsonLd = jsonLdFor("7855044-0");
		JsonNode gndIdentifier = Json.parse(jsonLd).get("gndIdentifier");
		assertTrue(gndIdentifier.isTextual());
	}

	@Test
	public void testSecondLevelTypePerson() throws FileNotFoundException {
		String jsonLd = jsonLdFor("100002617");
		List<?> types = Json.fromJson(Json.parse(jsonLd).get("type"), List.class);
		assertTrue(types.contains("AuthorityResource"));
		assertTrue(types.contains("Person"));
	}

	@Test
	public void testSecondLevelTypePlace() throws IOException {
		String id = "4074335-4";
		indexEntityFacts(id);
		String jsonLd = jsonLdFor(id);
		List<?> types = Json.fromJson(Json.parse(jsonLd).get("type"), List.class);
		assertTrue(types.contains("AuthorityResource"));
		assertTrue(types.contains("PlaceOrGeographicName"));
	}

	@Test
	public void testSpirit() throws FileNotFoundException {
		String jsonLd = jsonLdFor("7855044-0");
		List<?> types = Json.fromJson(Json.parse(jsonLd).get("type"), List.class);
		assertTrue(types.contains("Spirits"));
	}

	@Test
	public void testGeographicAreaCodeIsArray() throws FileNotFoundException {
		String jsonLd = jsonLdFor("7855044-0");
		assertTrue(Json.parse(jsonLd).get("geographicAreaCode").isArray());
	}

	@Test
	public void testPlaceOfBirthObjects() throws FileNotFoundException {
		String jsonLd = jsonLdFor("118820591");
		JsonNode place = Json.parse(jsonLd).get("placeOfBirth").elements().next();
		assertIsObjectWithIdAndLabel(place);
	}

	@Test
	public void testPlaceOfDeathObjects() throws FileNotFoundException {
		String jsonLd = jsonLdFor("118820591");
		JsonNode place = Json.parse(jsonLd).get("placeOfDeath").elements().next();
		assertIsObjectWithIdAndLabel(place);
	}

	@Test
	public void testRemoveNewlinesInLabels() throws FileNotFoundException {
		String jsonLd = jsonLdFor("118512676");
		JsonNode categories = Json.parse(jsonLd).get("gndSubjectCategory");
		assertTrue(categories.toString().contains("Literaturgeschichte (Schriftsteller)"));
	}

	@Test
	public void testIriFieldStructure() throws FileNotFoundException {
		String jsonLd = jsonLdFor("7855044-0");
		JsonNode terms = Json.parse(jsonLd).get("broaderTermGeneral");
		assertTrue(terms.isArray());
		assertTrue(terms.elements().hasNext());
		JsonNode first = terms.elements().next();
		assertIsObjectWithIdAndLabel(first);
		assertTrue(first.get("id").textValue().startsWith("https://d-nb.info/gnd/"));
		JsonNode label = first.get("label");
		assertTrue(label.isTextual());
		assertTrue(!label.toString().isEmpty());
	}

	@Test
	public void testOntologyLabelEnrichment() throws FileNotFoundException {
		String jsonLd = jsonLdFor("118624822");
		JsonNode area = Json.parse(jsonLd).get("geographicAreaCode").elements().next();
		assertIsObjectWithIdAndLabel(area);
		assertEquals("https://d-nb.info/standards/vocab/gnd/geographic-area-code#XD-US", area.get("id").textValue());
		assertEquals("USA", area.get("label").textValue());
	}

	@Test
	public void testRealIdentityStructure() throws FileNotFoundException {
		String jsonLd = jsonLdFor("118624822");
		JsonNode realIdentity = Json.parse(jsonLd).get("realIdentity");
		assertNotNull("realIdentity should exist", realIdentity);
		assertEquals(JsonNodeType.ARRAY, realIdentity.getNodeType());
		assertIsObjectWithIdAndLabel(realIdentity.elements().next());
	}

	@Test
	public void testGndLabelEnrichment() throws FileNotFoundException {
		String jsonLd = jsonLdFor("1081942517");
		JsonNode author = Json.parse(jsonLd).get("firstAuthor").elements().next();
		assertIsObjectWithIdAndLabel(author);
		assertEquals("https://d-nb.info/gnd/118624822", author.get("id").textValue());
		assertEquals("Twain, Mark", author.get("label").textValue());
	}

	@Test
	public void testSameAsCollectionEnrichment() throws IOException {
		String id = "16269284-5";
		String jsonLd = jsonLdFor(id);
		assertNotNull("JSON-LD should exist", jsonLd);
		assertTrue("sameAs should exist", Json.parse(jsonLd).has("sameAs"));
		JsonNode sameAs = Json.parse(jsonLd).get("sameAs").elements().next();
		assertTrue("sameAs should have a collection", sameAs.has("collection"));
		assertTrue("collection should not be empty", sameAs.get("collection").size() > 0);
		assertTrue("collection should have an id", sameAs.get("collection").has("id"));
		assertEquals("collection id should be a QID", "http://www.wikidata.org/entity/Q54919",
				sameAs.get("collection").get("id").textValue());
		assertTrue("collection should have an icon", sameAs.get("collection").has("icon"));
		assertTrue("collection should have a name", sameAs.get("collection").has("name"));
		assertTrue("collection should have an abbr", sameAs.get("collection").has("abbr"));
		assertTrue("collection should have a publisher", sameAs.get("collection").has("publisher"));
	}

	@Test
	public void testSameAsCollectionEnrichmentOrcid() throws IOException {
		String id = "1173843892";
		String jsonLd = jsonLdFor(id);
		assertNotNull("JSON-LD should exist", jsonLd);
		assertTrue("sameAs should exist", Json.parse(jsonLd).has("sameAs"));
		JsonNode sameAs = Json.parse(jsonLd).get("sameAs");
		assertTrue("sameAs should contain an ORCID entry", sameAs.toString().contains("http://orcid.org"));
		sameAs.elements().forEachRemaining(elem -> {
			assertTrue("sameAs should have a collection", elem.has("collection"));
			assertTrue("collection should not be empty", elem.get("collection").size() > 0);
			assertTrue("collection should have an id", elem.get("collection").has("id"));
			assertTrue("collection should have an icon", elem.get("collection").has("icon"));
			assertTrue("collection should have a name", elem.get("collection").has("name"));
			assertTrue("collection should have an abbr", elem.get("collection").has("abbr"));
			assertTrue("collection should have a publisher", elem.get("collection").has("publisher"));
		});
	}

	@Test
	public void testEntityFactsDepictionEnrichment() throws IOException {
		String id = "118624822";
		indexEntityFacts(id);
		String jsonLd = jsonLdFor(id);
		assertNotNull("JSON-LD should exist", jsonLd);
		JsonNode node = Json.parse(jsonLd);
		assertTrue("Enrichment for depiction should exist", node.has("depiction"));
		assertTrue("Depiction should be an array", node.get("depiction").isArray());
		JsonNode depiction = node.get("depiction").elements().next();
		assertTrue("Depiction should have an id", depiction.has("id"));
		assertTrue("Depiction should have a url", depiction.has("url"));
		assertTrue("Depiction should have a thumbnail", depiction.has("thumbnail"));
		assertFalse("thumbnail should not be an object", depiction.get("thumbnail").isObject());
	}

	@Test
	public void testEntityFactsSameAsEnrichment() throws IOException {
		String id = "118624822";
		indexEntityFacts(id);
		String jsonLd = jsonLdFor(id);
		assertNotNull("JSON-LD should exist", jsonLd);
		JsonNode node = Json.parse(jsonLd);
		JsonNode sameAsAll = node.get("sameAs");
		assertTrue("sameAs should contain original data",
				sameAsAll.toString().contains("http://dbpedia.org/resource/Mark_Twain"));
		assertTrue("Enrichment for sameAs should exist", sameAsAll.size() > 5);
		assertTrue("Enrichment for sameAs should exist", sameAsAll.size() > 5);
		JsonNode sameAs = sameAsAll.elements().next();
		assertTrue("sameAs collection should have an id", sameAs.get("collection").has("id"));
	}

	@Test
	public void testEntityFactsSameAsEnrichmentNoDuplicates() throws IOException {
		String id = "118820591";
		indexEntityFacts(id);
		String jsonLd = jsonLdFor(id);
		assertNotNull("JSON-LD should exist", jsonLd);
		JsonNode node = Json.parse(jsonLd);
		JsonNode sameAsAll = node.get("sameAs");
		Iterable<JsonNode> iterable = () -> sameAsAll.elements();
		Stream<JsonNode> stream = StreamSupport.stream(iterable.spliterator(), false);
		Set<String> ids = stream.map(n -> n.get("id").textValue()).collect(Collectors.toSet());
		assertEquals("sameAs entries should be unique", ids.size(), sameAsAll.size());
	}

	private void indexEntityFacts(String id) throws IOException {
		String json = Files.lines(Paths.get("test/entityfacts/" + id + ".json")).collect(Collectors.joining());
		TransportClient client = Convert.CLIENT;
		client.prepareIndex(config("index.entityfacts.index"), config("index.entityfacts.type")).setId(id)
				.setSource(json, XContentType.JSON).execute().actionGet();
		client.admin().indices().refresh(new RefreshRequest()).actionGet();
	}

	@Test
	public void testTriplesToFramedJsonLd() throws FileNotFoundException {
		Model model = ModelFactory.createDefaultModel();
		RDFDataMgr.read(model, in(
				"<https://d-nb.info/gnd/118820591> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://d-nb.info/standards/elementset/gnd#AuthorityResource> ."
						+ "<https://d-nb.info/gnd/118820591> <https://d-nb.info/standards/elementset/gnd#placeOfDeath> <https://d-nb.info/gnd/4005728-8> ."
						+ "<https://d-nb.info/gnd/118820591> <https://d-nb.info/standards/elementset/gnd#placeOfBirth> <https://d-nb.info/gnd/4005728-8> ."
						+ "<https://d-nb.info/gnd/4005728-8> <http://www.w3.org/2000/01/rdf-schema#label> \"Berlin\" ."),
				Lang.NTRIPLES);
		StringWriter out = new StringWriter();
		RDFDataMgr.write(out, model, Lang.JSONLD);
		try {
			ImmutableMap<String, String> frame = ImmutableMap.of("@type",
					"https://d-nb.info/standards/elementset/gnd#AuthorityResource", //
					"@embed", "@always");
			JsonLdOptions options = new JsonLdOptions();
			Object jsonLd = JsonUtils.fromString(out.toString());
			jsonLd = JsonLdProcessor.frame(jsonLd, new HashMap<>(frame), options);
			JsonNode jsonNode = Json.toJson(jsonLd);
			JsonNode birth = jsonNode.findValue("https://d-nb.info/standards/elementset/gnd#placeOfBirth");
			JsonNode death = jsonNode.findValue("https://d-nb.info/standards/elementset/gnd#placeOfDeath");
			assertEquals(birth.size(), death.size());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void assertIsObjectWithIdAndLabel(JsonNode json) {
		assertTrue("JSON node should be an object", json.isObject());
		assertTrue("JSON object should have an id", json.has("id"));
		assertTrue("JSON object should have a label", json.has("label"));
	}

	private InputStream in(String s) {
		return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
	}
}
