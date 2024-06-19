package ch.poliscore.service.storage;

import java.util.Optional;

import ch.poliscore.model.Persistable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.val;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@ApplicationScoped
public class DynamoDBPersistenceService implements PersistenceServiceIF
{
	public static final String TABLE_NAME = "poliscore";
	
	@Inject
    DynamoDbEnhancedClient ddb;
	
	public <T extends Persistable> void store(T obj)
	{
		@SuppressWarnings("unchecked")
		val table = ((DynamoDbTable<T>) ddb.table(TABLE_NAME, TableSchema.fromBean(obj.getClass())));
		
		table.putItem(obj);
	}
	
	public <T extends Persistable> Optional<T> retrieve(String id, Class<T> clazz)
	{
		@SuppressWarnings("unchecked")
		val table = ((DynamoDbTable<T>) ddb.table(TABLE_NAME, TableSchema.fromBean(clazz)));
		
		T result = table.getItem(Key.builder().partitionValue(id).build());
		
		if (result == null)
		{
			return Optional.empty();
		}
		else
		{
			return Optional.of(result);
		}
	}
}
