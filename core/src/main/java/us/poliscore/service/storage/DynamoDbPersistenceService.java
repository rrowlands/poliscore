package us.poliscore.service.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import us.poliscore.model.Persistable;

@ApplicationScoped
public class DynamoDbPersistenceService implements PersistenceServiceIF
{
	public static final String TABLE_NAME = "poliscore";
	
	@Inject
    DynamoDbEnhancedClient ddb;
	
	public <T extends Persistable> void put(T obj)
	{
		@SuppressWarnings("unchecked")
		val table = ((DynamoDbTable<T>) ddb.table(TABLE_NAME, TableSchema.fromBean(obj.getClass())));
		
		table.putItem(obj);
	}
	
	public <T extends Persistable> Optional<T> get(String id, Class<T> clazz)
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
	
	@Override
	public <T extends Persistable> PaginatedList<T> query(Class<T> clazz)
	{
		return query(clazz, -1, null);
	}
	
	@SneakyThrows
	public <T extends Persistable> PaginatedList<T> query(Class<T> clazz, int pageSize, String exclusiveStartKey)
	{
		@SuppressWarnings("unchecked")
		val table = ((DynamoDbTable<T>) ddb.table(TABLE_NAME, TableSchema.fromBean(clazz))).index(Persistable.OBJECT_BY_DATE_INDEX);
		
		val idClassPrefix =(String) clazz.getField("ID_CLASS_PREFIX").get(null);
		
		val request = QueryEnhancedRequest.builder()
				.queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue(idClassPrefix).build()));
		
		if (pageSize != -1) request.limit(pageSize);
		if (exclusiveStartKey != null) request.exclusiveStartKey(Map.of("date", AttributeValue.fromS(exclusiveStartKey)));
		
		var result = table.query(request.build());
		val mapLastEval = result.stream().findAny().get().lastEvaluatedKey();
		val lastEvaluatedKey = mapLastEval == null ? null : mapLastEval.get("date").s();
		
		List<T> objects = new ArrayList<T>();
		result.stream().forEach(p -> objects.addAll(p.items()));
		
		return new PaginatedList<T>(objects, pageSize, exclusiveStartKey, lastEvaluatedKey);
	}

	@Override
	public <T extends Persistable> boolean exists(String id, Class<T> clazz) {
		return get(id, clazz).isPresent();
	}
}
