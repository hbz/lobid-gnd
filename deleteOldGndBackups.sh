#!/bin/bash
set -eu

# This script runs daily via cron

echo "$(date) : Going to delete files older than 270 days"
find data/backup/ -name "GND-updates*" -type f -mtime +269 -delete -print

