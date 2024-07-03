#!/bin/bash

# Do not run with sudo
if [ "$EUID" -eq 0 ]
  then echo "Do not run as root"
  exit
fi

# Exit on error
set -e

export BUCKET_NAME=poliscore-website


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

cd webapp/src/main/webui
ng build
cd ../../../../

aws s3 rm s3://$BUCKET_NAME --recursive
aws s3 cp webapp/src/main/webui/dist/poliscore/browser s3://$BUCKET_NAME --recursive
