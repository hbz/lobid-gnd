package models;

import static org.joox.JOOX.$;
import static org.joox.JOOX.attr;
import static org.joox.JOOX.or;
import static org.joox.JOOX.selector;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.action.get.GetResponse;
import org.joox.Match;
import org.xml.sax.SAXException;

import controllers.HomeController;
import modules.IndexComponent;
import play.Logger;

public class GndOntology {

	@SuppressWarnings("serial")
	private static Map<String, String> labels = new HashMap<String, String>() {
		{
			put("depiction", "Darstellung");
			put("wikipedia", "Wikipedia");
			put("sameAs", "Siehe auch");
			put("type", "Entitätstyp");
			put("creatorOf", "Werke");
		}
	};

	public static IndexComponent index;

	static {
		try {
			process("conf/geographic-area-code.rdf");
			process("conf/gender.rdf");
			process("conf/gnd-sc.rdf");
			process("conf/gnd.rdf");
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
	 * @return The German label for sortId (e.g. Sammelhandschrift) if a label
	 *         was found, or the passed id
	 */
	public static String label(String id) {
		return id.startsWith(AuthorityResource.DNB_PREFIX) ? indexLabel(id) : ontologyLabel(id);
	}

	private static String ontologyLabel(String id) {
		String key = id.contains("#") ? id.split("#")[1] : id;
		String result = labels.get(key);
		return result == null ? id : result;
	}

	private static String indexLabel(String id) {
		id = id.substring(AuthorityResource.DNB_PREFIX.length());
		GetResponse response = index.client()
				.prepareGet(HomeController.config("index.name"), HomeController.config("index.type"), id).get();
		if (!response.isExists()) {
			Logger.warn("{} does not exists in index", id);
			return id;
		}
		return response.getSourceAsMap().get("preferredName").toString();
	}

	private static void process(String f) throws SAXException, IOException {
		Match match = $(new File(f)).find(or( //
				selector("Class"), //
				selector("ObjectProperty"), //
				selector("AnnotationProperty"), //
				selector("DatatypeProperty"), //
				selector("Concept")));
		match.forEach(c -> {
			String classId = c.getAttribute("rdf:about");
			if (classId.contains("#")) {
				String shortId = classId.split("#")[1];
				String label = $(c).find(or(//
						selector("label"), //
						selector("prefLabel"))).filter(attr("lang", "de")).content();
				checkAmibiguity(shortId, label);
				labels.put(shortId, label);
			}
		});
	}

	private static void checkAmibiguity(String shortId, String label) {
		String oldLabel = labels.get(shortId);
		if (oldLabel != null && !oldLabel.equals(label)) {
			throw new IllegalStateException(
					String.format("Ambiguous key: %s=%s -> %s=%s", shortId, oldLabel, shortId, label));
		}
	}

}
