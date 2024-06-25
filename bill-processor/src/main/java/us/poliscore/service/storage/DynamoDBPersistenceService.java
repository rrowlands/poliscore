package us.poliscore.service.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import us.poliscore.model.Persistable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@ApplicationScoped
public class DynamoDBPersistenceService implements PersistenceServiceIF
{
	public static final String TABLE_NAME = "poliscore";
	
	public static final String OBJECT_CLASS_INDEX = "ObjectClass";
	
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
	
	@SneakyThrows
	public <T extends Persistable> List<T> query(Class<T> clazz)
	{
		@SuppressWarnings("unchecked")
		val table = ((DynamoDbTable<T>) ddb.table(TABLE_NAME, TableSchema.fromBean(clazz))).index(OBJECT_CLASS_INDEX);
		
		val idClassPrefix =(String) clazz.getField("ID_CLASS_PREFIX").get(null);
		
		var result = table.query(QueryEnhancedRequest.builder()
				.queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(idClassPrefix).build()))
				.build());
		
		List<T> objects = new ArrayList<T>();
		result.stream().forEach(p -> objects.addAll(p.items()));
		
		return objects;
	}
}
