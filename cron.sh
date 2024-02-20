#!/bin/bash
# See http://redsymbol.net/articles/unofficial-bash-strict-mode/
# explicitly without "-e" for it should not exit immediately when failed but write a mail
set -uo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
# Execute via crontab by hduser@weywot1:
# 00 1 * * * [removed]  git/lobid-gnd ; bash -x cron.sh >> logs/cron.sh.log 2>&1"
# 50 05 * * * [removed] git/lobid-gnd ; bash -x cron.sh 1day >> logs/cron.sh.log 2>&1"


IFS=$'\n\t'
RECIPIENT=lobid-admin
START_UPDATE=$(tail -n1 GND-lastSuccessfulUpdate.txt)
unset http_proxy

if [ -n "${1-}" ] && [ "$1" = "1day" ]; then
  START_UPDATE=$(date --date='1 day ago'  +%Y-%m-%dT%H:%M:%SZ)
fi

sbt -mem 4000 "runMain apps.ConvertUpdates ${START_UPDATE}"

if [ -s GND-updates.jsonl ]; then
  sbt -Dindex.prod.name=gnd-test "runMain apps.Index updates"
  bash ./checkCompactedProperties.sh gnd-test

  # if check ok, index to productive instance:
  if [ $? -eq 0 ]; then
    sbt -Dindex.prod.name=gnd "runMain apps.Index updates"
    bash ./checkCompactedProperties.sh gnd
  fi
fi

