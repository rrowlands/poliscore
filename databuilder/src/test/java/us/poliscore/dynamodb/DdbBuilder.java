package us.poliscore.dynamodb;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.Legislator;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.MemoryPersistenceService;

@ApplicationScoped
public class DdbBuilder
{
	
	@Inject
    DynamoDbClient dbc;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private MemoryPersistenceService memory;
	
	@Inject
	private DynamoDbPersistenceService ddb;
	
	private boolean bootstrapped = false;
	
	public void defaultTestData()
	{
		if (!bootstrapped)
		{
			createTable();
			
			legService.importLegislators();
			
			ddb.store(memory.retrieve(PoliscoreUtil.BERNIE_SANDERS_ID, Legislator.class).orElseThrow());
			
			bootstrapped = true;
		}
	}
	
	public void createTable()
	{
		dbc.createTable(CreateTableRequest.builder()
				.tableName(DynamoDbPersistenceService.TABLE_NAME)
				.keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
				.attributeDefinitions(AttributeDefinition.builder().attributeName("id").attributeType("S").build())
				.provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
				.build());
	}
	
}
