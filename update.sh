#!/bin/bash

set -e
set -x

# TODO : Docker can't be running if we're importing, since it needs to hit remote...
#        What's the scenario where we actually want docker running... testing? Isn't that the mvn install?
#        deploy.sh definitely requires docker running
sudo docker ps

cd ../congress
git pull
python3 -m venv env
source env/bin/activate

usc-run govinfo --bulkdata=BILLSTATUS --congress=118
usc-run bills --govtrack --congres=118

cd ../poliscore

#open --background -a Docker # Start Docker Service
mvn install
#test -z "$(docker ps -q 2>/dev/null)" && osascript -e 'quit app "Docker"' # Stop Docker Service

cd databuilder && mvn exec:java -Dquarkus.devservices.enabled=false && cd ..

#open --background -a Docker # Start Docker Service
./deploy.sh
