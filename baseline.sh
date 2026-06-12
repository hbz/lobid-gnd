#!/bin/bash
set -uo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
# Call on server sol@quaoar11:~/git/lobid-gnd$ setsid nohup bash baseline.sh > baseline.log 2>&1 &

# details and defaults are configured in conf/application.conf

export TODAY=$(date +'%Y%m%d')

export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/

# get entityfacts baseline file
cd data/entityfacts/
wget --quiet https://data.dnb.de/opendata/authorities-gnd_entityfacts.ndjson.gz
gunzip < authorities-gnd_entityfacts.ndjson.gz > authorities-gnd_entityfacts.ndjson
cd ../..

# index entityfacts JSON
sbt --java-home $JAVA_HOME  \
  -Dindex.entityfacts.index=entityfacts_$TODAY \
  "runMain apps.Index entityfacts" \
  > IndexEntityfacts_$TODAY.log 2>&1

# clean up entityfacts baseline file
mv data/entityfacts/authorities-gnd_entityfacts.ndjson.gz data/entityfacts/authorities-gnd_entityfacts_$TODAY.ndjson.gz
rm data/entityfacts/authorities-gnd_entityfacts.ndjson

# get gnd_lds baseline files
cd data/gnd_lds
wget --quiet https://data.dnb.de/opendata/authorities-gnd-{geografikum,koerperschaft,kongress,person,sachbegriff,werk}_lds.rdf.gz

cd ../..
mkdir data/index/gnd_lds_$TODAY

# convert RDF_XML to JSON lines
sbt --java-home $JAVA_HOME \
  -Ddata.rdfmxl=data/gnd_lds \
  -Dindex.entityfacts.index=entityfacts_$TODAY \
  -Dindex.prod.name=gnd_$TODAY \
  -Ddata.jsonlines=data/index/gnd_lds_$TODAY \
  -Dindex.delete.baseline=GND-deprecated-baseline_$TODAY.txt \
  "runMain apps.ConvertBaseline" \
  > ConvertBaseline_$TODAY.log 2>&1

# clean up gnd_lds baseline files
mkdir data/backup/gnd_lds/gnd_lds_$TODAY
mv data/gnd_lds/authorities-*_lds.rdf.gz data/backup/gnd_lds/gnd_lds_$TODAY

# index JSON lines
sbt --java-home $JAVA_HOME \
  -Dindex.prod.name=gnd_$TODAY \
  -Ddata.jsonlines=data/index/gnd_lds_$TODAY \
  -Dindex.delete.baseline=GND-deprecated-baseline_$TODAY.txt \
  "runMain apps.Index baseline" \
  > IndexBaseline_$TODAY.log 2>&1

## index updates since last baseline (currently manual process)
## use OAI-PMH updates - run on test system (quaoar13) to avoid interfering with hourly updates!
# export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/
# export TODAY=20260527 # date used in the part above, see existing index
# export LAST_BASE=2026-02-17T12:00:00Z # get date from description on https://data.dnb.de/opendata/ (e.g. "Stand: 17.02.2026 14:15 Uhr UTC")
# setsid nohup sbt --java-home $JAVA_HOME -mem 4000 -Dindex.prod.name=gnd_$TODAY "runMain apps.ConvertUpdates $LAST_BASE" > ConvertUpdates_since_$LAST_BASE.log 2>&1 &
# setsid nohup sbt --java-home $JAVA_HOME -mem 4000 -Dindex.prod.name=gnd_$TODAY "runMain apps.Index updates" > IndexUpdates_since_$LAST_BASE.log 2>&1 &

## on prod system (quaoar11), make sure GND-lastSuccessfulUpdate.txt is earlier than the time you just started the updates (so we don't miss updates on prod)
## finally, switch elasticsearch 'gnd' alias to 'gnd_$TODAY' and 'entityfacts' alias to 'entityfacts_$TODAY'
