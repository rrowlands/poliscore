# poliscore

This is the official codebase which powers https://poliscore.us.

Poliscore is the world's first LLM-powered legislative rating service.

The website is built upon the following technologies and is a fully serverless application:

- Lambda with Quarkus
- Angular SSG
- DynamoDB
- S3

The project is fully open-sourced under MIT license and is owned and operated by HexTech Studios LLC.


This repo operates in two separate stages:

1. Database builder
2. Webapp

In the database builder stage, congressional data is fetched from congress using the open source github library "United States/Congress", also known as USC. This library is run in a sibling directory to the "poliscore" directory. The entrypoint for this database builder process is a shell script called "update.sh" which is at the root of the repo.

The update.sh shell script works at a high level as follows:
1. Update the USC library and fetch data from congress, fetches to a "data" directory inside the USC project
2. Run the "DatabaseBuilder" Java main class, inside the PoliScore project, which is the entrypoint for the PoliScore database builder routine. This routine is primarily responsible for "interpreting" bills, legislators, and political parties, by way of communicating with a remote Open AI service. The results of these interpretations are stored on S3. Bill and Legislator objects (with their inner "interpretation"s) are also built and deployed to DynamoDb, which serves as the primary webapp database. The S3 objects are primarily used for archival (and blue/green database syncing).
3. Produce webapp artifacts (like a site map) and then kick off the angular SSG (static site generation) process which builds the PoliScore front-end and then deploys the compiled front-end to S3.
4. A developer then manually checks the deployment, makes sure its good to go, and then runs ./switch-deployment.sh to make the changes live with a "blue / green" deployment paradigm.

This repo tracks and/or manages the following distinct datasets:
1. "Bill status" data. A bill status gives a high level overview of a bill, and includes information like the bill name, description, the current status, any actions, the sponsors and cosponsors.
2. "Bill Text" data. This data includes the full text of the bill. For congressional data, this data is typically scraped in a custom XML format, with a specific bill XSD. This allows for front-end customizations of the rendering of bill text.
3. Legislator data. This dataset is fetched directly from the USC 'legislators' Github Pages deployment (https://github.com/unitedstates/congress-legislators). At the moment it's downloaded and version controlled at "databuilder/src/main/resources/legislators-*.json"
4. Roll call data. Roll call data includes information about votes and other procedural "roll call" events within congress. For our purposes only official bill pass events votes are tracked and recorded, the rest are ignored. This dataset is fetched using USC scrapers, and ultimately comes from two different sources: the House, or the Senate. Both of these two sources have slightly different quirks in the way their data is represeneted: for example, in the Senate dataset the legislators are referred to by their LIS id, instead of the more standard "Bio Guide Id".

The PoliScore representation of these datasets, i.e. "Bill.java" and "Legislator.java", contain a "LegislativeNamespace" concept, which allows for support of additional "namespaces" for the data. When building ids for these objects, the LegislativeNamespace portion contributes the first "us/congress" part of their id, and the rest of the object id follows. This allows for support for additional datasets, for example "us/colorado", which could be used to store state level data.

This repo contains a unique "ObjectStorageServiceIF" which operates on "Persistable". This allows for a somewhat unified object persistance API where storage mechanisms are interwoven to create composite combinations such as the "LocalCachedS3Service", which utilizes the "LocalFilePersistanceService" to store local copies of objects fetched from the "S3PersistenceService".
