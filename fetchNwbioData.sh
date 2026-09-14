curl --header "Accept-Encoding: gzip" 'https://lobid.org/resources/search?q=inCollection.id:"http%3A%2F%2Flobid.org%2Fresources%2FHT014176012%23!"+AND+subject.componentList.type:Person&format=jsonl' \
| zcat \
| jq -r '.subject[]?.componentList[]? | select(.type[] == "Person" and .id) | .id' \
> conf/nwbio.txt
