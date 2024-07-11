package us.poliscore.service.storage;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.BeanTableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import us.poliscore.model.DdbDataPage;
import us.poliscore.model.Persistable;

@ApplicationScoped
public class DynamoDbPersistenceService implements PersistenceServiceIF
{
	public static final String TABLE_NAME = "poliscore";
	
	public static final String HEAD_PAGE = "0";
	
	@Data
	public static class DdbPage {
		public static DdbPage ALL = new DdbPage(null);
		
		public static DdbPage NONE = new DdbPage("__~`-=NONE=-`~__");
		
		private String page;
		
		private DdbPage(String page) {
			this.page = page;
		}
		
		public static DdbPage of(String page) {
			return new DdbPage(page);
		}
	}
	
	@Inject
    DynamoDbEnhancedClient ddbe;
	
	@Inject
	DynamoDbClient ddb;
	
	@SuppressWarnings("unchecked")
	@SneakyThrows
	public <T extends Persistable> void put(T obj)
	{
//		val table = ((DynamoDbTable<T>) ddbe.table(TABLE_NAME, TableSchema.fromBean(obj.getClass())));
		
		Map<String, Map<String, AttributeValue>> pages = new HashMap<String, Map<String, AttributeValue>>();
		
		for (Method getter : obj.getClass().getDeclaredMethods())
		{
		    if (getter.isAnnotationPresent(DdbDataPage.class))
	        {
		    	val a = getter.getAnnotation(DdbDataPage.class);
		    	
		    	getter.setAccessible(true);
		    	
		    	Object rawValue = getter.invoke(obj);
		    	
		    	AttributeValue val;
		    	
		    	if (getter.isAnnotationPresent(DynamoDbConvertedBy.class)) {
		    		val a2 = getter.getAnnotation(DynamoDbConvertedBy.class);
		    		
		    		val converter = a2.value().getDeclaredConstructor().newInstance();
		    		
		    		val = converter.transformFrom(rawValue);
		    	} else {
		            @SuppressWarnings("unchecked")
		            AttributeConverter<Object> converter = (AttributeConverter<Object>) new DefaultAttributeConverterProvider()
		                    .converterFor(EnhancedType.of(rawValue.getClass()));

		            val = converter.transformFrom(rawValue);
		    	}
		    	
		    	val values = pages.getOrDefault(a.value(), new HashMap<String, AttributeValue>());
		    	val fieldName = StringUtils.uncapitalize(getter.getName().replace("get", ""));
		    	values.put(fieldName, val);
		    	pages.put(a.value(), values);
	        }
		}
		
		@SuppressWarnings("rawtypes")
		Class clazz = obj.getClass();
		
		@SuppressWarnings("unchecked")
		Map<String, AttributeValue> objAttrs = new HashMap<String, AttributeValue>(TableSchema.fromBean(clazz).itemToMap(clazz.cast(obj), true));
		objAttrs.put("page", AttributeValue.fromS(HEAD_PAGE));
		
		for (String fieldName : pages.values().stream().map(v -> v.keySet()).reduce(new HashSet<String>(), (a,b) -> { a.addAll(b); return a; })) {
			objAttrs.remove(fieldName);
		}
		
		ddb.putItem(PutItemRequest.builder()
				.tableName(TABLE_NAME)
				.item(objAttrs)
				.build());
		
		for (String page : pages.keySet()) {
			val pageAttrs = pages.get(page);
			
			pageAttrs.put("id", AttributeValue.fromS(obj.getId()));
			pageAttrs.put("page", AttributeValue.fromS(page));
			
			ddb.putItem(PutItemRequest.builder()
					.tableName(TABLE_NAME)
					.item(pageAttrs)
					.build());
		}
	}
	
	@Override
	public <T extends Persistable> Optional<T> get(String id, Class<T> clazz)
	{
		return get(id, clazz, DdbPage.ALL);
	}
	
	@SuppressWarnings("unchecked")
	@SneakyThrows
	public <T extends Persistable> Optional<T> get(@NonNull String id, @NonNull Class<T> clazz, @NonNull DdbPage page)
	{
		val builder = Key.builder().partitionValue(id);
		
		if (page.equals(DdbPage.NONE)) {
			@SuppressWarnings("unchecked")
			val table = ((DynamoDbTable<T>) ddbe.table(TABLE_NAME, TableSchema.fromBean(clazz)));
			
			return Optional.ofNullable(table.getItem(builder.build()));
		} else {
			val schema = TableSchema.fromBean(clazz);
			
			var keyExpression = "id=:id";
			val eav = new HashMap<String,AttributeValue>(Map.of(":id", AttributeValue.fromS(id)));
			
			if (!page.equals(DdbPage.ALL)) {
				keyExpression += " AND page=:page";
				eav.put(":page", AttributeValue.fromS(page.getPage()));
			}
			
			val results = ddb.query(QueryRequest.builder()
					.tableName(TABLE_NAME)
					.keyConditionExpression(keyExpression)
					.expressionAttributeValues(eav)
					.build()).items().iterator();
			
//			T head = clazz.getConstructor().newInstance();
			
//			results.next().forEach((k,rawValue) -> {
//				copyFields(clazz, head, k, rawValue);
//			});
			
			T head = schema.mapToItem(results.next());
			
//			val annoPairs = Arrays.asList(clazz.getDeclaredMethods()).stream()
//					.filter(m -> m.isAnnotationPresent(DdbDataPage.class))
//					.map(m -> Pair.of(m, m.getAnnotation(DdbDataPage.class)))
//					.sorted((a,b) -> a.getLeft().getName().compareTo(b.getLeft().getName()))
//					.toList().iterator();
			
			while (results.hasNext()) {
				val next = results.next();
				
				for (val attr : next.keySet()) {
					if (!attr.equals("page") && !attr.equals("id")) {
						copyFields(clazz, schema, head, attr, next.get(attr));
					}
				}
			}
			
			return Optional.of(head);
		}
	}

	@SneakyThrows
	private <T extends Persistable> void copyFields(Class<T> clazz, BeanTableSchema<T> tableSchema,  T head, String attr, AttributeValue rawValue) {
		val getter = clazz.getMethod("get" + StringUtils.capitalize(attr));
		
		Object convertedVal;
		
//		if (getter.isAnnotationPresent(DynamoDbConvertedBy.class)) {
//			val a2 = getter.getAnnotation(DynamoDbConvertedBy.class);
//			
//			val converter = a2.value().getDeclaredConstructor().newInstance();
//			
//			convertedVal = converter.transformTo(rawValue);
//		} else {
//			
//			
//		    AttributeConverter<Object> converter = (AttributeConverter<Object>) new DefaultAttributeConverterProvider()
//		            .converterFor(EnhancedType.of(getter.getReturnType()));
//
//		    convertedVal = converter.transformTo(rawValue);
//		}
		
		convertedVal = tableSchema.converterForAttribute(attr).transformTo(rawValue);
		
//		try {
			val setter = clazz.getMethod("set" + StringUtils.capitalize(attr), getter.getReturnType());
			setter.invoke(head, convertedVal);
//		}
//		catch (NoSuchMethodException ex) {
//			val f = clazz.getField(attr);
//			f.setAccessible(true);
//			f.set(head, convertedVal);
//		}
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
		val table = ((DynamoDbTable<T>) ddbe.table(TABLE_NAME, TableSchema.fromBean(clazz))).index(Persistable.OBJECT_BY_DATE_INDEX);
		
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
