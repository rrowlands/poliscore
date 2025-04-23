#!/bin/bash

set -e

# Load deployment config from bluegreen.env
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="$SCRIPT_DIR/bluegreen.env"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "Error: Config file 'bluegreen.env' not found in $SCRIPT_DIR"
  exit 1
fi

set -o allexport
source "$CONFIG_FILE"
set +o allexport

# Ensure POLISCORE_DEPLOYMENT is valid
if [[ "$POLISCORE_DEPLOYMENT" != "1" && "$POLISCORE_DEPLOYMENT" != "2" ]]; then
  echo "Error: POLISCORE_DEPLOYMENT must be 1 or 2."
  exit 1
fi

###
# Swap the cloudfront deployment, which makes the dev code now live
###

export AWS_PAGER=""

if [ "$POLISCORE_DEPLOYMENT" -eq 1 ]; then
  NEW_ORIGIN="poliscore-website1.s3-website-us-east-1.amazonaws.com"
elif [ "$POLISCORE_DEPLOYMENT" -eq 2 ]; then
  NEW_ORIGIN="poliscore-website2.s3-website-us-east-1.amazonaws.com"
else
  echo "Invalid POLISCORE_DEPLOYMENT: Please specify 1 or 2."
  exit 1
fi

# CloudFront distribution IDs
DISTRIBUTION_1="E33KLI11KRM1QA"
DISTRIBUTION_2="E2MZLE77EVKTB"

# Function to update CloudFront distribution
update_distribution() {
  local DISTRIBUTION_ID=$1
  local DOMAIN_NAME=$2
  local CONFIG_JSON="config_$DISTRIBUTION_ID.json"

  # Get the current distribution config
  aws cloudfront get-distribution-config --id "$DISTRIBUTION_ID" > "$CONFIG_JSON"

  # Extract the ETag required for updates
  ETAG=$(jq -r '.ETag' "$CONFIG_JSON")

  # Modify the origin domain name
  jq --arg NEW_ORIGIN "$NEW_ORIGIN" '.DistributionConfig.Origins.Items[0].DomainName = $NEW_ORIGIN | .DistributionConfig' "$CONFIG_JSON" > "final_$CONFIG_JSON"

  # Update the CloudFront distribution
  aws cloudfront update-distribution --id "$DISTRIBUTION_ID" --if-match "$ETAG" --distribution-config file://final_$CONFIG_JSON

  # Cleanup temporary files
  rm "$CONFIG_JSON" "final_${CONFIG_JSON}"

  echo "Updated CloudFront distribution ($DISTRIBUTION_ID) to use origin: $NEW_ORIGIN"
}

# Update both distributions
update_distribution "$DISTRIBUTION_1" "poliscore.us"
update_distribution "$DISTRIBUTION_2" "www.poliscore.us"

echo "CloudFront distributions updated successfully."

###
# Update the source code to point to the new dev deployment
###

if [ "$POLISCORE_DEPLOYMENT" == "1" ]; then
  sed -i '' 's|poliscore1|poliscore2|g' ./core/src/main/java/us/poliscore/service/storage/DynamoDbPersistenceService.java
  #sed -i '' 's|poliscore-prod1|poliscore-prod2|g' ./core/src/main/java/us/poliscore/service/storage/S3PersistenceService.java
  sed -i '' 's|Poliscore1ipgeolocation683E-N4epMkZ8yjtM|Poliscore2ipgeolocation94A2-SonL4BB2tvpP|g' ./webapp/src/main/java/us/poliscore/service/SecretService.java
  sed -i '' 's|Poliscore1|Poliscore2|g' ./cdk/src/main/java/us/poliscore/CdkApp.java
  sed -i '' 's|https://y5i3jhm7k5vy67elvzly4b3b240kjwlp.lambda-url.us-east-1.on.aws/|https://5hta4jxn7q6cfcyxnvz4qmkyli0tambn.lambda-url.us-east-1.on.aws/|g' ./webapp/src/main/webui/src/app/app.config.ts
fi

if [ "$POLISCORE_DEPLOYMENT" == "2" ]; then
  sed -i '' 's|poliscore2|poliscore1|g' ./core/src/main/java/us/poliscore/service/storage/DynamoDbPersistenceService.java
  #sed -i '' 's|poliscore-prod2|poliscore-prod1|g' ./core/src/main/java/us/poliscore/service/storage/S3PersistenceService.java
  sed -i '' 's|Poliscore2ipgeolocation94A2-SonL4BB2tvpP|Poliscore1ipgeolocation683E-N4epMkZ8yjtM|g' ./webapp/src/main/java/us/poliscore/service/SecretService.java
  sed -i '' 's|Poliscore2|Poliscore1|g' ./cdk/src/main/java/us/poliscore/CdkApp.java
  sed -i '' 's|https://5hta4jxn7q6cfcyxnvz4qmkyli0tambn.lambda-url.us-east-1.on.aws/|https://y5i3jhm7k5vy67elvzly4b3b240kjwlp.lambda-url.us-east-1.on.aws/|g' ./webapp/src/main/webui/src/app/app.config.ts
fi


###
# Toggle POLISCORE_DEPLOYMENT in bluegreen.env
###

if [ "$POLISCORE_DEPLOYMENT" == "1" ]; then
  sed -i '' 's/POLISCORE_DEPLOYMENT=1/POLISCORE_DEPLOYMENT=2/' "$CONFIG_FILE"
elif [ "$POLISCORE_DEPLOYMENT" == "2" ]; then
  sed -i '' 's/POLISCORE_DEPLOYMENT=2/POLISCORE_DEPLOYMENT=1/' "$CONFIG_FILE"
fi

echo "POLISCORE_DEPLOYMENT value in bluegreen.env toggled."

