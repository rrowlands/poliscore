#!/bin/bash

set -e
set -x

sudo docker ps

cd ../congress
git pull
python3 -m venv env
source env/bin/activate

CONGRESS=119

usc-run govinfo --bulkdata=BILLSTATUS --congress=$CONGRESS
usc-run bills --govtrack --congress=$CONGRESS

cd ../poliscore

mvn install

cd databuilder
mvn exec:java -Dquarkus.devservices.enabled=false -Dvertx.options.warningExceptionTime=-1
cd ..

./deploy.sh
