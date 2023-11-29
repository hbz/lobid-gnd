#!/bin/bash
set -uo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/

cd ../rpb
bash transformRppd.sh
cd -
sbt "runMain apps.Index baseline"
