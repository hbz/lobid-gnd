#!/bin/bash
set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
IFS=$'\n\t'

JSONL="GND-deprecated.jsonl"
OUTPUT="GND-deprecated.txt"

curl "http://lobid.org/gnd/search?q=deprecatedUri:*&format=jsonl" > $JSONL
cat $JSONL | sed -n 's/.*deprecatedUri":\[\([^]]*\).*/\1/p' | tr , \\n | cut -d '/' -f 5 | cut -d '"' -f 1 > $OUTPUT
