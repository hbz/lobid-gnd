package models;

public class LinkWithImage implements Comparable<LinkWithImage> {
	public String url;
	public String image;
	public String label;

	LinkWithImage(String url, String icon, String label) {
		this.url = url;
		this.image = icon;
		this.label = label.replace("Gemeinsame Normdatei (GND) im Katalog der Deutschen Nationalbibliothek",
				"Deutsche Nationalbibliothek (DNB)");
	}

	@Override
	public int hashCode() {
		return url.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LinkWithImage that = (LinkWithImage) obj;
		return that.url.equals(this.url);
	}

	@Override
	public String toString() {
		return "Link [url=" + url + ", icon=" + image + ", label=" + label + "]";
	}

	@Override
	public int compareTo(LinkWithImage that) {
		return that.url.compareTo(this.url);
	}
}