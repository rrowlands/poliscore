#!/bin/bash

CURRENT_YEAR=$1
NEXT_YEAR=$2
PREVIOUS_CONGRESS=$(( (CURRENT_YEAR - 1789) / 2 + 1))
NEXT_CONGRESS=$(( (NEXT_YEAR - 1789) / 2 + 1 ))

sed -i '' "s|DEPLOYMENT_YEAR = \"$CURRENT_YEAR\";|DEPLOYMENT_YEAR = \"$NEXT_YEAR\";|g" ./core/src/main/java/us/poliscore/PoliscoreUtil.java
sed -i '' "s|return $CURRENT_YEAR;|return $NEXT_YEAR;|g" ./webapp/src/main/webui/src/app/config.service.ts
sed -i '' "s|/$CURRENT_YEAR/|/$NEXT_YEAR/|g" ./webapp/src/main/webui/angular.json
sed -i '' "s|export YEAR=$CURRENT_YEAR|export YEAR=$NEXT_YEAR|g" ./deploy.sh
sed -i '' "s|CONGRESS=$PREVIOUS_CONGRESS|CONGRESS=$NEXT_CONGRESS|g" ./update.sh
echo "MANUAL STEP : Make sure this year is in the list of SUPPORTED_CONGRESSES in PoliScoreUtil"
echo "MANUAL STEP : Don't forget to change the deployment year in the cloudfront routing script"
echo "MANUAL STEP : Make sure to update the list of supported congresses in the front-end"

