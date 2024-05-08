import rdflib
from rdflib import Graph

g = rdflib.Graph()

g.parse("geographic-area-code_20191015.ttl", format="ttl")

qres = g.query(
    """
    PREFIX skos: <http://www.w3.org/2004/02/skos/core#>

    SELECT ?uri ?label
       WHERE {
        ?uri a skos:Concept ;
            skos:prefLabel ?label
       }""")

with open("countryCode2LabelMap.tsv", "a") as output:
    for row in qres:
            output.write("%s\t%s" % row) #  separate ?concept and ?label with tab 
            output.write("\n") # add a new line delimiter to start new line