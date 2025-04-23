#!/bin/bash

# Do not run with sudo
if [ "$EUID" -eq 0 ]
  then echo "Do not run as root"
  exit
fi

# Exit on error
set -e

# Load deployment config
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="$SCRIPT_DIR/bluegreen.env"

if [[ ! -f "$CONFIG_FILE" ]]; then
  echo "Error: Config file 'bluegreen.env' not found in $SCRIPT_DIR"
  exit 1
fi

set -o allexport
source "$CONFIG_FILE"
set +o allexport

if [[ "$POLISCORE_DEPLOYMENT" != "1" && "$POLISCORE_DEPLOYMENT" != "2" ]]; then
  echo "Error: POLISCORE_DEPLOYMENT must be 1 or 2."
  exit 1
fi

export NODE_OPTIONS="--max-old-space-size=8192"
export BUCKET_NAME="poliscore-website$POLISCORE_DEPLOYMENT"
export YEAR=2026


if [ "$1" != "view" ]; then
  docker ps

  mvn clean install

  cd webapp
  quarkus build --native --no-tests -Dquarkus.native.container-build=true
  cd ..

  cd cdk
  cdk deploy --require-approval never
  cd ..
fi

if [ "$1" != "backend" ]; then
  cd webapp/src/main/webui
  ng build --base-href /$YEAR/
  cd ../../../../

  aws s3 rm s3://$BUCKET_NAME/$YEAR --recursive
  aws s3 cp webapp/src/main/webui/dist/poliscore/browser s3://$BUCKET_NAME/$YEAR --recursive
fi
