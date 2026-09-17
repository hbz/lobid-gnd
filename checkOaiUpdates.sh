#!/bin/bash
# check-oai-pmh-updates.sh
# Checks if the number of modified resources from OAI-PMH equals the number in lobid-gnd
# for a given day.

set -uo pipefail

# Default to yesterday if no date is provided
DEFAULT_DATE=$(date --date='1 day ago' +%Y-%m-%d)
READ_DATE="${1:-$DEFAULT_DATE}"

# Parse the input date to ensure proper format
if ! parsed_date=$(date -d "$READ_DATE" +%Y-%m-%d 2>/dev/null); then
    echo "Error: Invalid date format '$READ_DATE'. Please use YYYY-MM-DD."
    exit 1
fi
READ_DATE="$parsed_date"

# Convert the input date (CET/CEST) to UTC for OAI-PMH queries
# For 2026-09-15 in CEST (UTC+2), this means:
# 00:00:01 CEST = 22:00:01 UTC (previous day)
# 23:59:59 CEST = 21:59:59 UTC (same day)

# Get the UTC datetime for 00:00:01 CET on the given date (start of day)
FROM_CET="${READ_DATE}T00:00:01"
# Get the UTC datetime for 23:59:59 CET on the given date (end of day)
UNTIL_CET="${READ_DATE}T23:59:59"

# Convert to UTC using the IANA timezone Europe/Berlin,
# which correctly handles CET (UTC+1) / CEST (UTC+2) depending on the date.
# Step 1: interpret the local time as Europe/Berlin and get the epoch.
# Step 2: format the epoch in UTC.
FROM_EPOCH=$(TZ=Europe/Berlin date -d "${FROM_CET}" +%s 2>/dev/null)
UNTIL_EPOCH=$(TZ=Europe/Berlin date -d "${UNTIL_CET}" +%s 2>/dev/null)
FROM_UTC=$(date -u -d "@${FROM_EPOCH}" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null)
UNTIL_UTC=$(date -u -d "@${UNTIL_EPOCH}" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null)

echo "Input date in CET/CEST: $READ_DATE"
echo "OAI-PMH from (UTC):     $FROM_UTC"
echo "OAI-PMH until (UTC):    $UNTIL_UTC"
echo ""

# The lobid-gnd API query uses the same date in CET, so no conversion needed
echo "lobid-gnd search date (CET): $READ_DATE"
echo ""

# OAI-PMH endpoint (DNB's official endpoint)
OAI_PMH_URL="https://services.dnb.de/oai/repository"

# lobid-gnd API endpoint
LOBD_API_URL="https://lobid.org/gnd/search"

echo "============================================"
echo "Checking OAI-PMH updates for date: $READ_DATE"
echo "============================================"
echo ""
echo "Querying OAI-PMH interface..."
echo "From (UTC): $FROM_UTC"
echo "Until (UTC): $UNTIL_UTC"
echo ""

# Query OAI-PMH and count results using ListIdentifiers
# The completeListSize is in the response body
OAI_RESPONSE=$(curl -s -G \
    --data-urlencode "verb=ListIdentifiers" \
    --data-urlencode "metadataPrefix=RDFxml" \
    --data-urlencode "from=$FROM_UTC" \
    --data-urlencode "until=$UNTIL_UTC" \
    --data-urlencode "set=authorities" \
    "$OAI_PMH_URL")

# Check if curl succeeded
if [ $? -ne 0 ] || [ -z "$OAI_RESPONSE" ]; then
    echo "ERROR: Failed to fetch OAI-PMH response"
    exit 1
fi

# Extract completeListSize from the response.
# Be lenient about the exact tag/attribute format, e.g.
#   <oai-pmh:completeListSize>3221</oai-pmh:completeListSize>
#   <completeListSize size="3221"/>
#   <oai:completeSize>3221</oai:completeSize>
OAI_COUNT=$(echo "$OAI_RESPONSE" | grep -oP 'complete(List)?Size[^0-9]*\K[0-9]+' | head -1)

# If we couldn't extract completeListSize, fall back to counting
# the identifier elements (this is the complete list only if the
# response has no resumptionToken)
if [ -z "$OAI_COUNT" ]; then
    if echo "$OAI_RESPONSE" | grep -q 'resumptionToken'; then
        echo "WARNING: Could not extract completeListSize from a paginated response"
    else
        OAI_COUNT=$(echo "$OAI_RESPONSE" | grep -o '<identifier>' | wc -l)
    fi
fi

# Default to 0 if we couldn't extract a count
if [ -z "$OAI_COUNT" ]; then
    OAI_COUNT=0
    echo "WARNING: Could not extract completeListSize from OAI-PMH response"
fi

echo "OAI-PMH record count: $OAI_COUNT"
echo ""

echo "Querying lobid-gnd API..."
echo "Search URL: ${LOBD_API_URL}?q=describedBy.dateModified:${READ_DATE}"
echo ""

# Query lobid-gnd API
LOBD_RESPONSE=$(curl -s \
    "${LOBD_API_URL}?q=describedBy.dateModified:${READ_DATE}&format=json")

# Extract totalItems from the JSON response
LOBD_COUNT=$(echo "$LOBD_RESPONSE" | grep -oP '"totalItems"\s*:\s*\K[0-9]+' || echo "0")

# If grep didn't find anything, try with python
if [ -z "$LOBD_COUNT" ] || [ "$LOBD_COUNT" = "0" ]; then
    LOBD_COUNT=$(echo "$LOBD_RESPONSE" | python3 -c "import sys, json; d=json.load(sys.stdin); print(d.get('totalItems', 0))" 2>/dev/null || echo "0")
fi

echo "lobid-gnd record count: $LOBD_COUNT"
echo ""
echo "============================================"
echo "COMPARISON RESULT"
echo "============================================"

if [ "$OAI_COUNT" -eq "$LOBD_COUNT" ]; then
    echo "✓ SUCCESS: Counts match!"
    echo "  OAI-PMH:    $OAI_COUNT"
    echo "  lobid-gnd:  $LOBD_COUNT"
    exit 0
else
    echo "✗ MISMATCH: Counts differ!"
    echo "  OAI-PMH:    $OAI_COUNT"
    echo "  lobid-gnd:  $LOBD_COUNT"
    DIFF=$((OAI_COUNT - LOBD_COUNT))
    if [ $DIFF -lt 0 ]; then
        echo "  Difference: $DIFF (lobid-gnd has fewer records)"
    else
        echo "  Difference: +$DIFF (lobid-gnd has more records)"
    fi
    exit 1
fi
