#!/bin/bash

set -e
set -x

cd ../congress
python3 -m venv env
source env/bin/activate

usc-run govinfo --bulkdata=BILLSTATUS --congress=118
usc-run bills --govtrack --congres=118

cd ../poliscore
mvn install
cd databuilder && mvn exec:java && cd ..

