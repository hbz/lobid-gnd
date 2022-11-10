#!/bin/bash
set -uo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
# Call on server sol@quaoar1:~/git/lobid-gnd$ setsid nohup bash baseline.sh > baseline.log 2>&1 &

# details and defaults are configured in conf/application.conf

export TODAY=$(date +'%Y%m%d')

# get entityfacts baseline file
cd data/entityfacts/
wget https://data.dnb.de/opendata/authorities_entityfacts.jsonld.gz
gunzip < authorities_entityfacts.jsonld.gz > authorities_entityfacts.jsonld
cd ../..

# index entityfacts JSON
sbt \
  -Dindex.entityfacts.index=entityfacts_$TODAY \
  "runMain apps.Index entityfacts" \
  > IndexEntityfacts_$TODAY.log 2>&1

# clean up entityfacts baseline file
mv data/entityfacts/authorities_entityfacts.jsonld.gz data/entityfacts/authorities_entityfacts_$TODAY.jsonld.gz

# get gnd_lds baseline files
cd data/gnd_lds
wget https://data.dnb.de/opendata/authorities-gnd-{geografikum,koerperschaft,kongress,person,sachbegriff,werk}_lds.rdf.gz

# validate the xml before we start the long-running conversion
for file in *.rdf.gz
do
    zcat $file | xmllint --noout -
    if [ $? -eq 0 ]
    then
        echo "Validated XML: $file"
    else
        echo "XML validation FAILED: $file"
        exit 1
    fi
done

cd ../..
mkdir data/index/gnd_lds_$TODAY

# convert RDF_XML to JSON lines
sbt \
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
sbt \
  -Dindex.prod.name=gnd_$TODAY \
  -Ddata.jsonlines=data/index/gnd_lds_$TODAY \
  -Dindex.delete.baseline=GND-deprecated-baseline_$TODAY.txt \
  "runMain apps.Index baseline" \
  > IndexBaseline_$TODAY.log 2>&1

# index updates since last baseline (currently manual process)
# export TODAY=20211126 # date used in the part above, see existing index
# export LAST_BASE=20201013 # automate: get date from description on https://data.dnb.de/opendata/ (e.g. "Stand: 13.06.2021")
# mkdir data/index/gnd_since_$LAST_BASE
# cp data/backup/GND-updates_2021*.jsonl data/index/gnd_since_$LAST_BASE # etc.; alt: OAI-PMH
# setsid nohup sbt \
#  -Dindex.prod.name=gnd_$TODAY \
#  -Ddata.jsonlines=data/index/gnd_since_$LAST_BASE \
#  -Dindex.delete.baseline=GND-deprecated-updates.txt \
#  "runMain apps.Index baseline" \
#  > IndexBaseline_since_$LAST_BASE.log 2>&1 &

# a more automatable alternative might be to use OAI-PMH updates
# export LAST_BASE=2020-10-13 # get date from description on https://data.dnb.de/opendata/ (e.g. "Stand: 13.06.2021")
# sbt "runMain apps.ConvertUpdates $LAST_BASE"
# sbt "runMain apps.Index updates"

## finally, switch 'gnd' alias to 'gnd_$TODAY'
