#!/bin/bash
# See #171.
# Shall be started after indexing of an update or a fulldump.
# Checks if there are uncompacted field. If so, mail an alert.
# First parameter is the name of the index that will be checked. Without
# parameter, the _newest_ gnd-prefixed index is determined and checked - this
# can be used after indexing a fulldump.

set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
IFS=$'\n\t'
ES="weywot5.hbz-nrw.de:9200"
INDEX=""
if [ -n "${1-}" ]; then
	INDEX=$1
fi
SEARCH=http
RECIPIENT=lobid-admin

if [ -z ${INDEX-} ]; then
	INDEX=$(curl $ES/_settings |jq .[].settings.index.provided_name |grep gnd | tr -d \" | sort |tail -n1)
fi
echo "check index-name: $INDEX"

MESSAGE=$(curl $ES/$INDEX/_mapping | jq -r 'paths | map(.|tostring)|join(".")' |grep $SEARCH.*type$ || exit 0)

if [ -z "${MESSAGE-}" ]; then
	echo "no uncompacted fields found"
	exit 0
fi

echo "there are uncompacted fields: $MESSAGE"

mail -s "Alert GND: found not compacted field(s)!" "$RECIPIENT@hbz-nrw.de" -a "From: sol@quaoar1" << EOF
$MESSAGE
EOF
exit 1
