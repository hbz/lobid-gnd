#!/bin/bash
set -eu

cd /home/sol/git/lobid-gnd

# find "newest" directory by it's name
# extract date from directory name
dir_date=$(find data/backup/gnd_lds/ -type d | sort | tail -1 | rev | cut -d'/' -f1 | rev | cut -d'_' -f3)

if [[ -z "$dir_date" ]]; then
	echo "$(date) : Going to delete files older than 270 days"
	find data/backup/ -name "GND-updates*" -type f -mtime +269 -delete -print

else
	# create temp file with $dir_date as timestamp
	touch -t ${dir_date}0000 /tmp/$dir_date

	echo "$(date) : Going to delete files older than $dir_date"
	find data/backup/ -name "GND-updates*" -type f ! -newer /tmp/$dir_date -delete -print
	
	# delete temp file
	rm /tmp/$dir_date
fi
