# rppd: Die Rheinland-Pfälzische Personendatenbank

## About

Authority database for persons of RPB

[![](https://github.com/hbz/rppd/actions/workflows/build.yml/badge.svg)](https://github.com/hbz/rppd/actions?query=workflow%3ABuild)

## Setup

### Prerequisites

Java 8

`sbt 0.13` or newer --- [download sbt](http://www.scala-sbt.org/download.html)

Elasticsearch 5.6.x (configured in `application.conf`, see `.github/workflows/build.yml` for local setup)

### Build

Get the code, change into the project directory, and run the tests:

`git clone https://github.com/hbz/rppd.git ; cd rppd`

If needed start elasticsearch:

```bash
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-5.6.3.tar.gz
tar -xzf elasticsearch-5.6.3.tar.gz
./elasticsearch-5.6.3/bin/elasticsearch &
```

(If elastic search is already running and you want to start a new es: `ps -ef | grep elas` and `kill [PID]`.)

Run tests:

`sbt test`

### Data

Production RPPD data is transformed and indexed in `transformAndIndexRppd.sh`.

This requires a local <https://github.com/hbz/rpb> repo, in the same directory as the rppd repo.

### Web

In 'rppd', run the web application:

`sbt run` (or `sbt start` for prod mode)

Open <http://localhost:9000>

To use the test data, disable `deleteIndexes` in `test/modules/IndexTest.java` and re-run the tests.

To use an existing Elasticsearch index on a different machine, configure the `prod` section in `conf/application.conf`.

### Eclipse

To set up an Eclipse project, first generate the Eclipse config for your machine:

`sbt "eclipse with-source=true"`

Then import the project in Eclipse: "File" \> "Import" \> "Existing Projects into Workspace".
