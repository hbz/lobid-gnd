search=http
index="gnd-staging"
recipient=lobid-admin

message=$(curl weywot5.hbz-nrw.de:9200/$index/_mapping | jq -r 'paths | map(.|tostring)|join(".")' |grep $search.*properties. |grep -vE '.properties(.id|.label)(.*)$' |grep -vE '.properties$')

if [ -z "$message" ]; then
        echo "empty - return"
        exit 0
fi
mail -s "GND: not compacted fields!" "$recipient@hbz-nrw.de" -a "From: sol@quaoar1" << EOF
        $message
EOF
