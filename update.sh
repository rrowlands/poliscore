#!/bin/bash

set -e
set -x

NAMESPACE=${1:-"us/congress"} # Default to us/congress if no first argument
SESSION_IDENTIFIER=${2:-"118"}   # Default to 118 if no second argument

echo "NAMESPACE: $NAMESPACE"
echo "SESSION_IDENTIFIER: $SESSION_IDENTIFIER"

sudo docker ps

# Removed USC data fetching block:
# cd ../congress
# git pull
# python3 -m venv env
# source env/bin/activate
# pip install .
# usc-run govinfo --bulkdata=BILLSTATUS --congress=$CONGRESS
# usc-run bills --govtrack --congress=$CONGRESS
# usc-run votes --congress=$CONGRESS

cd ../poliscore # This assumes poliscore is in the parent directory of where update.sh is.
                # If update.sh is in poliscore root, this should be just 'cd .' or removed if already in root.
                # Based on original, it seems update.sh might be in a scripts dir, and poliscore is one level up.
                # For now, keeping cd ../poliscore as per original structure relative to the removed congress block.
                # If poliscore is the root, this needs adjustment.

mvn install

# Update USC Legislator files
#curl -sSfL https://unitedstates.github.io/congress-legislators/legislators-current.json -o databuilder/src/main/resources/legislators-current.json
#curl -sSfL https://unitedstates.github.io/congress-legislators/legislators-historical.json -o databuilder/src/main/resources/legislators-historical.json


cd databuilder
#mvn exec:java -Dquarkus.devservices.enabled=false -Dquarkus.launch.devmode=false -Dvertx.options.warningExceptionTime=-1 -Dtest-containers.disabled=true

mvn exec:java \
  -Dquarkus.devservices.enabled=false \
  -Dquarkus.launch.devmode=false \
  -Dvertx.options.warningExceptionTime=-1 \
  -Dtest-containers.disabled=true \
  -Dpoliscore.namespace="$NAMESPACE" \
  -Dpoliscore.session="$SESSION_IDENTIFIER" \
2>&1 | ./vertx.silencer



#mvn package -Dquarkus.package.type=uber-jar
#java -jar target/databuilder-0.0.1-SNAPSHOT-runner.jar

cd ..

./deploy.sh
