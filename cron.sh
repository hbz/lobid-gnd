#!/bin/bash
# See http://redsymbol.net/articles/unofficial-bash-strict-mode/
# explicitly without "-e" for it should not exit immediately when failed but write a mail
set -uo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
# Execute via crontab by hduser@weywot1:
# 00 1 * * * ssh sol@quaoar1 "cd /home/sol/git/lobid-gnd ; bash -x cron.sh >> logs/cron.sh.log 2>&1"

IFS=$'\n\t'
RECIPIENT=lobid-admin
sbt -mem 4000 "runMain apps.ConvertUpdates $(tail -n1 GND-lastSuccessfulUpdate.txt)"
sbt "runMain -Dindex.prod.name=gnd-test apps.Index updates"
bash ./checkCompactedProperties.sh gnd-test

# if check ok, index to productive instance:
if [ $? -eq 0 ]; then
	sbt -Dindex.prod.name=gnd "runMain apps.Index updates"
	bash ./checkCompactedProperties.sh gnd
fi
