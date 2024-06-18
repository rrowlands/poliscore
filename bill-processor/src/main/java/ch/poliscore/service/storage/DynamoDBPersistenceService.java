package ch.poliscore.service.storage;

import java.util.Optional;

import ch.poliscore.model.Legislator;
import ch.poliscore.model.LegislatorInterpretation;
import ch.poliscore.model.Persistable;
import io.quarkus.amazon.dynamodb.enhanced.runtime.NamedDynamoDbTable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

@ApplicationScoped
public class DynamoDBPersistenceService implements PersistenceServiceIF
{
	public static final String TABLE_NAME = "poliscore";
	
//	private DynamoDbEnhancedClient ddb;
	
	@Inject
    @NamedDynamoDbTable(DynamoDBPersistenceService.TABLE_NAME)
    DynamoDbTable<Legislator> legislatorTable;
	
//	@Inject
//    @NamedDynamoDbTable(DynamoDBPersistenceService.TABLE_NAME)
//    DynamoDbTable<LegislatorInterpretation> legislatorInterpTable;
	
//	protected DynamoDbEnhancedClient getClient()
//	{
//		if (ddb == null)
//		{
//			Region region = Region.US_EAST_1;
//	        DynamoDbClient standardClient = DynamoDbClient.builder()
//	                .region(region)
//	                .build();
//	        
//	        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
//	        	    .dynamoDbClient(standardClient)
//	        	    .build();
//	        
//	        ddb = enhancedClient;
//		}
//		
//		return ddb;
//	}
	
	public <T extends Persistable> void store(T obj)
	{
//		@SuppressWarnings("unchecked")
//		val table = ((DynamoDbTable<T>) ddb.table(TABLE_NAME, TableSchema.fromBean(obj.getClass())));
//		
//		table.putItem(obj);
		
		if (obj instanceof Legislator)
		{
			legislatorTable.putItem((Legislator) obj);
		}
		else
		{
			throw new UnsupportedOperationException();
		}
	}
	
	public <T extends Persistable> Optional<T> retrieve(String id, Class<T> clazz)
	{
//		@SuppressWarnings("unchecked")
//		val table = ((DynamoDbTable<T>) ddb.table(TABLE_NAME, TableSchema.fromBean(clazz)));
//		
//		T result = table.getItem(Key.builder().partitionValue(id).build());
//		
//		if (result == null)
//		{
//			return Optional.empty();
//		}
//		else
//		{
//			return Optional.of(result);
//		}
		
		throw new UnsupportedOperationException();
	}
}
