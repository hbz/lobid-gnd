package models;

import static org.joox.JOOX.$;
import static org.joox.JOOX.attr;
import static org.joox.JOOX.or;
import static org.joox.JOOX.selector;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.joox.Match;
import org.xml.sax.SAXException;

public class GndOntology {

	private static String file = "conf/gnd.rdf";

	@SuppressWarnings("serial")
	private static Map<String, String> labels = new HashMap<String, String>() {
		{
			put("wikipedia", "Wikipedia");
			put("sameAs", "Siehe auch");
			put("type", "Entitätstyp");
		}
	};

	static {
		try {
			Match match = $(new File(file)).find(or( //
					selector("Class"), //
					selector("ObjectProperty"), //
					selector("AnnotationProperty"), //
					selector("DatatypeProperty")));
			match.forEach(c -> {
				String classId = c.getAttribute("rdf:about");
				if (classId.contains("#")) {
					String id = classId.split("#")[1];
					String label = $(c).find("label").filter(attr("lang", "de")).content();
					labels.put(id, label);
				}
			});
		} catch (SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	public static String label(String name) {
		String result = labels.get(name);
		return result == null ? name : result;
	}

}
