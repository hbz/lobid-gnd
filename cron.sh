#!/bin/bash
set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
IFS=$'\n\t'

# Execute via crontab by hduser@weywot1:
# 00 1 * * * ssh sol@quaoar1 "cd /home/sol/git/lobid-gnd ; bash -x cron.sh >> logs/cron.sh.log 2>&1"

sbt "runMain apps.ConvertUpdates $(tail -n1 GND-lastSuccessfulUpdate.txt)"

# copy and index to stage:
scp GND-updates.jsonl weywot2:git/lobid-gnd/
ssh sol@weywot2 "cd /home/sol/git/lobid-gnd ; sh restart.sh lobid-gnd;
 tail -f ./logs/application.log |grep -q "main - Application started (Prod)" ; ./checkCompactedProperties.sh"

# index to procutive instance:
if [  $? -eq 0 ]; then
	#sh restart.sh lobid-gnd
	MESSAGE="check war gut :)"
	else MESSAGE="check fail :("
fi

mail -s "Alert GND: test" "$rece@hbz-nrw.de" -a "From: sol@weywot2" << EOF
$MESSAGE
EOF
