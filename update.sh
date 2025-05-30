#!/bin/bash

set -e
set -o pipefail
set -x

sudo docker ps

cd ../congress
git pull
python3 -m venv env
source env/bin/activate
pip install .

CONGRESS=119

usc-run govinfo --bulkdata=BILLSTATUS --congress=$CONGRESS
usc-run bills --govtrack --congress=$CONGRESS
usc-run votes --congress=$CONGRESS

cd ../poliscore

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
2>&1



#mvn package -Dquarkus.package.type=uber-jar
#java -jar target/databuilder-0.0.1-SNAPSHOT-runner.jar

cd ..

./deploy.sh
