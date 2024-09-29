
Building a PoliScore database is a very complicated and manual process, some of which requires S3 resources. AWS hardware must be spun up before attempting this process using the CDK resources. Unfortunately, without heavy knowledge of AWS and the rest of the PoliScore stack it is hard for the community to do this at the moment. More work must be done to make this process accessible by the community at large.

1. Update USC legislators in databuilder/src/main/resources (these don't HAVE to be included manually into our source. They can be fetched on demand from the Github pages source)
2. Fetch legislator photos (Can also be fetched on demand from USC source)
3. Fetch bill status
4. Fetch bill text
5. Run CBODataFetcher
6. Interpret bills (twice), using BatchBillRequestGenerator and BathOpenAIResponseImporter 
7. Interpret legislators, using BatchLegislatorRequestGenerator and BathOpenAIResponseImporter
8. Interpret party stats, using SessionStatsBuilder.java and BathOpenAIResponseImporter
9. Run WebappDataGenerator
10. Configure deployment target with 'switch-deployment.sh' shell script
11. Run 'deploy.sh backend' to update the Lambda webserver
12. Run 'deploy.sh view' to update the S3 static site
13. Test on deployed target
14. Update CloudFormation to point to the deployment target
15. Swap deployment target to prepare for next development cycle
