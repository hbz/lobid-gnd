#!/bin/bash
set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/

cd ../rpb
sbt "runMain rpb.ETL conf/rppd-to-strapi.flux IN_FILE=RPB-Export_HBZ_Bio.txt OUT_FILE=output-rppd-strapi.ndjson"
sbt "runMain rpb.ETL conf/rppd-to-lobid.flux"
cd -
sbt "runMain apps.Index baseline"
