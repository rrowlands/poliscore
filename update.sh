#!/bin/bash

set -e
set -x

# TODO : Docker can't be running if we're importing, since it needs to hit remote...
#        What's the scenario where we actually want docker running... testing? Isn't that the mvn install?
#        deploy.sh definitely requires docker running
docker ps

cd ../congress
python3 -m venv env
source env/bin/activate

usc-run govinfo --bulkdata=BILLSTATUS --congress=118
usc-run bills --govtrack --congres=118

cd ../poliscore
mvn install
cd databuilder && mvn exec:java && cd ..

./deploy.sh
