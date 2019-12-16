#!/bin/bash
# See http://redsymbol.net/articles/unofficial-bash-strict-mode/
# explicitly without "-e" for it should not exit immediately when failed but write a mail
set -uo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
# Execute via crontab by hduser@weywot1:
# 00 1 * * * ssh sol@quaoar1 "cd /home/sol/git/lobid-gnd ; bash -x cron.sh >> logs/cron.sh.log 2>&1"

IFS=$'\n\t'
RECIPIENT=lobid-admin
sbt -mem 4000 "runMain apps.ConvertUpdates $(tail -n1 GND-lastSuccessfulUpdate.txt)"

# copy and index to stage, and check:
scp GND-updates.jsonl weywot2:git/lobid-gnd/
scp GND-deprecated-updates.txt weywot2:git/lobid-gnd/
ssh sol@weywot2 'cd /home/sol/git/lobid-gnd ; sbt "runMain apps.Index updates" ; bash ./checkCompactedProperties.sh gnd'

# if check ok, index to productive instance:
if [  $? -eq 0 ]; then
	sbt "runMain apps.Index updates"
	else MESSAGE="check fail :("
mail -s "Alert GND: test bad " "$RECIPIENT@hbz-nrw.de" << EOF
Because of these uncompacted fields the data is not indexed into the productive service:

$MESSAGE
EOF
fi
