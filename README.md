#README

## About

lobid-gnd: access GND+EntityFacts data as JSON-LD over HTTP.

[![](https://github.com/hbz/lobid-gnd/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/hbz/lobid-gnd/actions?query=workflow%3ABuild)

## Setup

### Prerequisites

`sbt 0.13` or newer --- [download sbt](http://www.scala-sbt.org/download.html)

Elasticsearch 5.6.x (configured in `application.conf`)

### Build

Get the code, change into the project directory, and run the tests:

`git clone https://github.com/hbz/lobid-gnd.git ; cd lobid-gnd ; sbt test`

### Data

The are three data sources involved:

Entity Facts (JSON-LD over HTTP), GND baseline (RDF-XML over HTTP), and GND updates (RDF-XML over OAI-PMH).

#### Entity Facts

Set up a location for the Entity Facts input data:

`mkdir entityfacts ; cd entityfacts`

Get the latest Entity Facts data from the DNB (see [https://data.dnb.de/opendata/](https://data.dnb.de/opendata/)):

`wget https://data.dnb.de/opendata/authorities_entityfacts.jsonld.gz`

Unpack the data:

`gunzip < authorities_entityfacts.jsonld.gz > authorities_entityfacts.jsonld`

Go back to the root directory:

`cd ..`

Index the data, passing the index name:

`sbt -Dindex.entityfacts.index=entityfacts_20210120 "runMain apps.Index entityfacts"`

For configuration details and defaults, see 'conf/application.conf'.

#### GND Baseline

##### Get the RDF data

Set up a location for the input data:

`mkdir input_data; cd input_data`

Set 'data.rdfxml' in 'conf/application.conf' to the 'input_data' location.

Get the GND RDF/XML source data from <https://data.dnb.de/opendata/>:

`wget https://data.dnb.de/opendata/authorities-{geografikum,koerperschaft,kongress,person,sachbegriff,werk}_lds.rdf.gz`

This should give you 6 local files ending with '.rdf.gz'. Go back to the project root directory:

`cd ..`

##### Convert RDF/XML to JSON

Set up a location for the index data:

`mkdir index_data`

Set 'data.jsonlines' in 'conf/application.conf' to the 'index_data' location.

Set 'index.boot' in 'conf/application.conf' to an existing index. This index will be used to get labels during the conversion process.

Set 'index.prod' in 'conf/application.conf' to a non-existing index. This index name will be used in the indexing data created during conversion.

Convert the data to JSON-LD lines, the index data format:

`sbt "runMain apps.ConvertBaseline"`

To be able to log out from the server while the conversion is running, we actually use (see full usage details in baseline.sh):

`setsid nohup sbt "runMain apps.ConvertBaseline" &`

This should create 6 '\*.jsonl' files in 'index_data'.

##### Index the JSON data

If the 'index.prod' configured in 'application.conf' does not exists, a new index will be created.

To start the indexing, run:

`sbt "runMain apps.Index baseline"`

#### Updates

##### Get and convert the updates

Updates are pulled via [the DNB OAI-PMH interface](http://www.dnb.de/DE/Service/DigitaleDienste/OAI/oai_node.html).

Pass one or two arguments: get updates since (and optionally until) a given date:

`sbt "runMain apps.ConvertUpdates 2022-06-22 2022-06-23"`

The date of the most recent update is stored in 'GND-lastSuccessfulUpdate.txt' (can be changed in the config).

The original downloaded data and the converted data are stored in separate files. To convert the data again without downloading it, use the steps described above under 'Convert RDF/XML to JSON' with the update RDF data.

##### Index the updates

To index the updates run:

`sbt "runMain apps.Index updates"`

See 'application.conf' for details on the configured file names etc.

### Web

In 'lobid-gnd', run the web application:

`sbt run`

Open <http://localhost:9000/gnd>

### Eclipse

To set up an Eclipse project, first generate the Eclipse config for your machine:

`sbt "eclipse with-source=true"`

Then import the project in Eclipse: "File" \> "Import" \> "Existing Projects into Workspace".
