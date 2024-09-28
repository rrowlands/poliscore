
Building a PoliScore database is a very complicated and manual process, some of which requires S3 resources. AWS hardware must be spun up before attempting this process using the CDK resources. Unfortunately, without heavy knowledge of AWS and the rest of the PoliScore stack it is hard for the community to do this at the moment. More work must be done to make this process accessible by the community at large.

1. Update USC legislators in databuilder/src/main/resources (these don't HAVE to be included manually into our source. They can be fetched on demand from the Github pages source)
2. Fetch legislator photos (Can also be fetched on demand from USC source)
3. Fetch bill status
4. Fetch bill text
5. Interpret bills (twice), using BatchBillRequestGenerator and BathOpenAIResponseImporter 
6. Interpret legislators, using BatchLegislatorRequestGenerator and BathOpenAIResponseImporter
7. Interpret party stats, using SessionStatsBuilder.java
8. Run WebappDataGenerator
9. Configure deployment target with 'switch-deployment.sh' shell script
10. Run 'deploy.sh backend' to update the Lambda webserver
11. Run 'deploy.sh view' to update the S3 static site
12. Test on deployed target
13. Update CloudFormation to point to the deployment target
14. Swap deployment target to prepare for next development cycle
