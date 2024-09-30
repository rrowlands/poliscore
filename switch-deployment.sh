#!/bin/bash

if [ "$1" == "1" ]; then
  sed -i '' 's|poliscore2|poliscore1|g' ./core/src/main/java/us/poliscore/service/storage/DynamoDbPersistenceService.java
  #sed -i '' 's|poliscore-prod2|poliscore-prod1|g' ./core/src/main/java/us/poliscore/service/storage/S3PersistenceService.java
  sed -i '' 's|Poliscore2ipgeolocation94A2-SonL4BB2tvpP|Poliscore1ipgeolocation683E-N4epMkZ8yjtM|g' ./webapp/src/main/java/us/poliscore/service/SecretService.java
  sed -i '' 's|poliscore-website2|poliscore-website1|g' ./deploy.sh
  sed -i '' 's|Poliscore2|Poliscore1|g' ./cdk/src/main/java/us/poliscore/CdkApp.java
  sed -i '' 's|https://5hta4jxn7q6cfcyxnvz4qmkyli0tambn.lambda-url.us-east-1.on.aws/|https://y5i3jhm7k5vy67elvzly4b3b240kjwlp.lambda-url.us-east-1.on.aws/|g' ./webapp/src/main/webui/src/app/app.config.ts
fi

if [ "$1" == "2" ]; then
  sed -i '' 's|poliscore1|poliscore2|g' ./core/src/main/java/us/poliscore/service/storage/DynamoDbPersistenceService.java
  #sed -i '' 's|poliscore-prod1|poliscore-prod2|g' ./core/src/main/java/us/poliscore/service/storage/S3PersistenceService.java
  sed -i '' 's|Poliscore1ipgeolocation683E-N4epMkZ8yjtM|Poliscore2ipgeolocation94A2-SonL4BB2tvpP|g' ./webapp/src/main/java/us/poliscore/service/SecretService.java
  sed -i '' 's|poliscore-website1|poliscore-website2|g' ./deploy.sh
  sed -i '' 's|Poliscore1|Poliscore2|g' ./cdk/src/main/java/us/poliscore/CdkApp.java
  sed -i '' 's|https://y5i3jhm7k5vy67elvzly4b3b240kjwlp.lambda-url.us-east-1.on.aws/|https://5hta4jxn7q6cfcyxnvz4qmkyli0tambn.lambda-url.us-east-1.on.aws/|g' ./webapp/src/main/webui/src/app/app.config.ts
fi
