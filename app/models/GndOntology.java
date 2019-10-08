/* Copyright 2017-2018 Fabian Steeg, hbz. Licensed under the EPL 2.0 */

package models;

import static org.joox.JOOX.$;
import static org.joox.JOOX.attr;
import static org.joox.JOOX.or;
import static org.joox.JOOX.selector;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.joox.Match;
import org.xml.sax.SAXException;

import com.typesafe.config.ConfigFactory;

import controllers.HomeController;
import play.Logger;

public class GndOntology {

	private static final TransportClient CLIENT = new PreBuiltTransportClient(
			Settings.builder().put("cluster.name", HomeController.config("index.boot.cluster")).build());

	static {
		ConfigFactory.parseFile(new File("conf/application.conf")).getStringList("index.boot.hosts").forEach((host) -> {
			try {
				CLIENT.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), 9300));
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		});
	}

	@SuppressWarnings("serial")
	private static Map<String, String> labels = new HashMap<String, String>() {
		{
			put("depiction", "Darstellung");
			put("wikipedia", "Wikipedia");
			put("sameAs", "Siehe auch");
			put("type", "Entitätstyp");
			put("creatorOf", "Werke");
			// no current German SKOS translation, see
			// https://www.w3.org/2004/02/skos/translations
			put("broadMatch", "Oberbegriff");
			put("exactMatch", "Entspricht");
			put("relatedMatch", "Verwandter Begriff");
		}
	};

	static {
		try {
			process("conf/geographic-area-code.rdf");
			process("conf/gender.rdf");
			process("conf/gnd-sc.rdf");
			process("conf/gnd.rdf");
			process("conf/agrelon.rdf");
		} catch (SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	private static Map<String, List<String>> properties = new HashMap<>();

	static final List<String> ADDITIONAL_PROPERTIES = Arrays.asList("type", "sameAs", "preferredName", "variantName",
			"depiction");

	static {
		try {
			loadProperties("conf/gnd.rdf");
		} catch (SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	private static Map<String, String> types = new HashMap<>();

	static {
		try {
			loadTypes("conf/gnd.rdf");
		} catch (SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Get labels for IDs from:<br/>
	 * <br/>
	 * http://d-nb.info/standards/elementset/gnd <br/>
	 * http://d-nb.info/standards/vocab/gnd/geographic-area-code.html <br/>
	 * http://d-nb.info/standards/vocab/gnd/gnd-sc.html <br/>
	 * https://d-nb.info/standards/vocab/gnd/gender.html <br/>
	 * 
	 * @param id
	 *            The full URI or substring after # for an element in one vocab
	 *            (e.g. CollectiveManuscript)
	 * @return The German label for sortId (e.g. Sammelhandschrift) if a label was
	 *         found, or the passed id
	 */
	public static String label(String id) {
		try {
			return id.startsWith(AuthorityResource.DNB_PREFIX) ? indexLabel(id) : ontologyLabel(id);
		} catch (Exception e) {
			Logger.error("Could not get label for {}: {}", id, e.getMessage());
			return id;
		}
	}

	/**
	 * Get properties used in the domain of the given type from:<br/>
	 * <br/>
	 * http://d-nb.info/standards/elementset/gnd <br/>
	 * Additional types supported: AuthorityResource, Person
	 * 
	 * @param type
	 *            The short type, e.g. SubjectHeading
	 * @return The short IDs for properties in the domain of the given type
	 */
	public static List<String> properties(String type) {
		List<String> result = type.isEmpty() ? properties.entrySet().stream().flatMap(e -> e.getValue().stream())
				.distinct().collect(Collectors.toList()) : properties.get(type);
		result = result == null ? new ArrayList<>() : result;
		result.addAll(ADDITIONAL_PROPERTIES);
		return new ArrayList<>(new TreeSet<>(result));
	}

	/**
	 * @param property
	 *            The short property, e.g. placeOfBirth
	 * @return The type of values for the given property (if they are GND
	 *         entities, otherwise null), e.g. PlaceOrGeographicName
	 */
	public static String type(String property) {
		return types.get(property);
	}

	private static String ontologyLabel(String id) {
		String key = id.contains("#") ? id.split("#")[1] : id;
		String result = labels.get(key);
		return result == null ? id : result;
	}

	private static String indexLabel(String id) {
		id = id.substring(AuthorityResource.DNB_PREFIX.length());
		GetResponse response = CLIENT
				.prepareGet(HomeController.config("index.boot.name"), HomeController.config("index.type"), id).get();
		if (!response.isExists()) {
			Logger.warn("{} does not exists in index", id);
			return id;
		}
		return response.getSourceAsMap().get("preferredName").toString();
	}

	private static void process(String f) throws SAXException, IOException {
		Match match = $(new File(f)).find(or( //
				selector("Class"), //
				selector("Property"), //
				selector("ObjectProperty"), //
				selector("AnnotationProperty"), //
				selector("DatatypeProperty"), //
				selector("SymmetricProperty"), //
				selector("TransitiveProperty"), //
				selector("Concept")));
		match.forEach(c -> {
			String classId = c.getAttribute("rdf:about");
			if (classId.contains("#")) {
				String shortId = classId.split("#")[1];
				String label = $(c).find(or(//
						selector("label"), //
						selector("prefLabel"))).filter(attr("lang", "de")).content();
				if (label != null) {
					labels.put(shortId, label.replaceAll("\\s+", " ").replace("hat ", ""));
				}
			}
		});
	}

	private static void loadProperties(String f) throws SAXException, IOException {
		Match match = $(new File(f)).find(or( //
				selector("Property"), //
				selector("ObjectProperty"), //
				selector("AnnotationProperty"), //
				selector("DatatypeProperty")));
		match.forEach(property -> {
			String propertyId = property.getAttribute("rdf:about");
			if ($(property).find(selector("deprecated")).isEmpty() && propertyId.contains("#")) {
				String shortPropertyId = propertyId.split("#")[1];
				Match domains = $(property).find(selector("domain"));
				domains.forEach(domain -> {
					String type = domain.getAttribute("rdf:resource");
					if (type != null && type.contains("#")) {
						put(type.split("#")[1], shortPropertyId);
					}
				});
				$(domains).find(selector("Class")).forEach(dc -> {
					String type = dc.getAttribute("rdf:about");
					if (type != null && type.contains("#")) {
						put(type.split("#")[1], shortPropertyId);
					}
				});
			}
		});
		addNonOntologyTypes();
	}

	private static void loadTypes(String f) throws SAXException, IOException {
		Match match = $(new File(f)).find(or( //
				selector("Property"), //
				selector("ObjectProperty"), //
				selector("AnnotationProperty"), //
				selector("DatatypeProperty")));
		match.forEach(property -> {
			String propertyId = property.getAttribute("rdf:about");
			if (propertyId.contains("#")) {
				String shortPropertyId = propertyId.split("#")[1];
				$(property).find(selector("range")).forEach(domain -> {
					String type = domain.getAttribute("rdf:resource");
					String shortType;
					if (type != null && type.startsWith("http://d-nb.info/standards/elementset/gnd#")
							&& !(shortType = type.split("#")[1]).equals("Literal")) {
						types.put(shortPropertyId, shortType);
					}
				});
			}
		});
	}

	private static void addNonOntologyTypes() {
		List<String> person = new ArrayList<>();
		person.addAll(properties.get("DifferentiatedPerson"));
		person.addAll(properties.get("UndifferentiatedPerson"));
		properties.put("Person", person);
		List<String> all = properties.values().stream().flatMap(List::stream).distinct().collect(Collectors.toList());
		properties.put("AuthorityResource", all);
	}

	private static void put(String type, String property) {
		List<String> propertiesForType = properties.get(type);
		if (propertiesForType == null) {
			propertiesForType = new ArrayList<String>();
			properties.put(type, propertiesForType);
		}
		if (!property.matches("(preferred|variant)Name.+")) {
			propertiesForType.add(property);
		}
	}

}
