#!/bin/bash
# See http://redsymbol.net/articles/unofficial-bash-strict-mode/
# explicitly without "-e" for it should not exit immediately when failed but write a mail
set -uo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
# Execute via crontab by hduser@weywot1:
# 40 * * * * [removed]  git/lobid-gnd ; bash -x cron.sh >> logs/cron.sh.log 2>&1"
# 50 05 * * * [removed] git/lobid-gnd ; bash -x cron.sh 1day >> logs/cron.sh.log 2>&1"

IFS=$'\n\t'
RECIPIENT=lobid-admin
START_UPDATE=$(tail -n1 GND-lastSuccessfulUpdate.txt)
TEST_INDEX=gnd-test
INDEX=gnd
unset http_proxy

export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/

if [ -n "${1-}" ] && [ "$1" = "1day" ]; then
  START_UPDATE=$(date --date='1 day ago'  +%Y-%m-%dT%H:%M:%SZ)
fi

if [ -f GND-skippedUpdates.txt ]; then
  # convert START_UPDATE to epoch seconds for comparison with skipped update periods
  START_UPDATE_EPOCH=$(date --date="$START_UPDATE" +%s) || {
    echo "Invalid start update time: $START_UPDATE" >&2
    exit 1
  }
  LINE_NUMBER=0
  # read the skipped update periods from GND-skippedUpdates.txt
  # EXTRA is used to detect if there are more than 2 fields in a line
  while IFS=$' \t' read -r SKIPPED_FROM SKIPPED_UNTIL EXTRA || \
      # process the final line even if it has no trailing newline, unless it is empty.
      [ -n "${SKIPPED_FROM-}${SKIPPED_UNTIL-}${EXTRA-}" ]; do
    LINE_NUMBER=$((LINE_NUMBER + 1))
    # skip empty lines and comments
    if [ -z "${SKIPPED_FROM-}${SKIPPED_UNTIL-}${EXTRA-}" ]; then
      continue
    fi
    # check if the line has exactly 2 fields (SKIPPED_FROM and SKIPPED_UNTIL)
    if [ -z "${SKIPPED_FROM-}" ] || [ -z "${SKIPPED_UNTIL-}" ] || [ -n "${EXTRA-}" ]; then
      echo "Invalid skipped update period on line $LINE_NUMBER of GND-skippedUpdates.txt" >&2
      exit 1
    fi
    # convert SKIPPED_FROM and SKIPPED_UNTIL to epoch seconds for comparison
    date --date="$SKIPPED_FROM" +%s >/dev/null 2>&1 || {
      echo "Invalid skipped update start time on line $LINE_NUMBER of GND-skippedUpdates.txt" >&2
      exit 1
    }
    SKIPPED_UNTIL_EPOCH=$(date --date="$SKIPPED_UNTIL" +%s 2>/dev/null) || {
      echo "Invalid skipped update end time on line $LINE_NUMBER of GND-skippedUpdates.txt" >&2
      exit 1
    }
    # if the last skipped update UNTIL is after the current START_UPDATE, update START_UPDATE to SKIPPED_UNTIL
    if [ "$SKIPPED_UNTIL_EPOCH" -gt "$START_UPDATE_EPOCH" ]; then
      START_UPDATE=$SKIPPED_UNTIL
      START_UPDATE_EPOCH=$SKIPPED_UNTIL_EPOCH
    fi
  done < GND-skippedUpdates.txt
fi

sbt --java-home $JAVA_HOME -mem 4000 "runMain apps.ConvertUpdates ${START_UPDATE}"

if [ -s GND-updates.jsonl ]; then
  sbt --java-home $JAVA_HOME -Dindex.prod.name=$TEST_INDEX "runMain apps.Index updates"
  bash ./checkCompactedProperties.sh $TEST_INDEX

  # if check ok, index to productive instance:
  if [ $? -eq 0 ]; then
    sbt --java-home $JAVA_HOME -Dindex.prod.name=$INDEX "runMain apps.Index updates"
    bash ./checkCompactedProperties.sh $INDEX
  fi
fi
