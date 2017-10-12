#!/bin/bash
set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
IFS=$'\n\t'

# Execute via crontab by hduser@weywot1:
# 00 1 * * * ssh sol@quaoar1 "cd /home/sol/git/lobid-gnd ; bash -x cron.sh >> logs/cron.sh.log 2>&1"

sbt "runMain apps.ConvertUpdates $(tail -n1 GND-lastSuccessfulUpdate.txt)"
sh restart.sh lobid-gnd
scp GND-updates.jsonl weywot2:git/lobid-gnd/
ssh sol@weywot2 "cd /home/sol/git/lobid-gnd ; sh restart.sh lobid-gnd"
