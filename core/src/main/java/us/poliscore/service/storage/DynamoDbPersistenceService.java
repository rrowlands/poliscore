package us.poliscore.service.storage;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
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
import us.poliscore.model.Persistable;
import us.poliscore.model.dynamodb.DdbDataPage;
import us.poliscore.model.dynamodb.DdbKeyProvider;
import us.poliscore.model.dynamodb.DdbListPage;

@ApplicationScoped
public class DynamoDbPersistenceService implements PersistenceServiceIF
{
	public static final String TABLE_NAME = "poliscore1";
	
	public static final String HEAD_PAGE = "0";
	
	@Data
	public static class DdbPage {
		public static DdbPage ALL = new DdbPage(null);
		
		public static DdbPage HEAD = new DdbPage(HEAD_PAGE);
		
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
	
	private Map<Class<?>, BeanTableSchema<?>> schemas  = new HashMap<Class<?>, BeanTableSchema<?>>();
	
	@SuppressWarnings("unchecked")
	private <T extends Persistable> BeanTableSchema<T> getSchema(Class<T> clazz) {
		if (!schemas.containsKey(clazz)) {
			schemas.put(clazz, TableSchema.fromBean(clazz));
		}
		
		return (BeanTableSchema<T>) schemas.get(clazz);
	}
	
	@SuppressWarnings("unchecked")
	@SneakyThrows
	public <T extends Persistable> void put(T obj)
	{
//		val table = ((DynamoDbTable<T>) ddbe.table(TABLE_NAME, getSchema(obj.getClass())));
		
		Map<String, Map<String, AttributeValue>> pages = new HashMap<String, Map<String, AttributeValue>>();
		val schema = (BeanTableSchema<T>) getSchema(obj.getClass());
		
		var hasSortKey = schema.tableMetadata().primarySortKey().isPresent();
		
		if (!hasSortKey) {
			for (Method getter : obj.getClass().getDeclaredMethods())
			{
			    if (getter.isAnnotationPresent(DdbDataPage.class) || getter.isAnnotationPresent(DdbListPage.class))
		        {
			    	val attr = StringUtils.uncapitalize(getter.getName().replace("get", ""));
			    	
	//		    	getter.setAccessible(true); // TODO : Necessary? Not sure.
			    	Object rawValue = getter.invoke(obj);
			    	
			    	if (getter.isAnnotationPresent(DdbDataPage.class)) {
			    		val page = getter.getAnnotation(DdbDataPage.class).value();
			    		
			    		// incompatible types: software.amazon.awssdk.enhanced.dynamodb.AttributeConverter<T> cannot be converted to software.amazon.awssdk.enhanced.dynamodb.AttributeConverter<java.lang.Object>
//			    		val val = ((AttributeConverter<Object>) schema.converterForAttribute(attr)).transformFrom(rawValue);
			    		
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
			    		
				    	
				    	val values = pages.getOrDefault(page, new HashMap<String, AttributeValue>());
				    	values.put(attr, val);
				    	pages.put(page, values);
			    	} else {
			    		throw new UnsupportedOperationException();
			    		
	//		    		val limit = getter.getAnnotation(DdbListPage.class).value();
	//		    		val all = (Collection<?>) rawValue;
	//		    		
	//		    		val it = all.iterator();
	//		    		var page = new ArrayList<Object>();
	//		    		int i = 0;
	//		    		int pnum = 1;
	//		    		while (it.hasNext()) {
	//		    			if (i >= limit) {
	////		    				pages.put(String.valueOf(pnum), page);
	////		    				val val = schema.converterForAttribute(attr).transformFrom((T) rawValue);
	//		    				
	//					    	val values = pages.getOrDefault(page, new HashMap<String, AttributeValue>());
	//					    	values.put(attr, val);
	//					    	pages.put(page, values);
	//		    				
	//		    				i = 0;
	//		    				page = new ArrayList<Object>();
	//		    				pnum++;
	//		    			}
	//		    			page.add(it.next());
	//		    			i++;
	//		    		}
			    	}
		        }
			}
		}
		
		@SuppressWarnings("rawtypes")
		Class clazz = obj.getClass();
		
		@SuppressWarnings("unchecked")
		Map<String, AttributeValue> objAttrs = new HashMap<String, AttributeValue>(getSchema(clazz).itemToMap(clazz.cast(obj), true));
		
//		if (!hasSortKey) {
			objAttrs.put("page", AttributeValue.fromS(HEAD_PAGE));
			
			// Remove all page data from head object
			for (String fieldName : pages.values().stream().map(v -> v.keySet()).reduce(new HashSet<String>(), (a,b) -> { a.addAll(b); return a; })) {
				objAttrs.remove(fieldName);
			}
//		}
		
		// Apply head object
		ddb.putItem(PutItemRequest.builder()
				.tableName(TABLE_NAME)
				.item(objAttrs)
				.build());
		
//		if (!hasSortKey) {
			// Apply all pages
			for (String page : pages.keySet()) {
				val pageAttrs = pages.get(page);
				
				pageAttrs.put("id", AttributeValue.fromS(obj.getId()));
				pageAttrs.put("page", AttributeValue.fromS(page));
				
				ddb.putItem(PutItemRequest.builder()
						.tableName(TABLE_NAME)
						.item(pageAttrs)
						.build());
			}
//		}
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
		val schema = getSchema(clazz);
		var hasSortKey = schema.tableMetadata().primarySortKey().isPresent();
		
		if (hasSortKey) {
			val key = (Key) Arrays.asList(clazz.getDeclaredMethods()).stream().filter(m -> m.isAnnotationPresent(DdbKeyProvider.class)).findFirst().orElseThrow().invoke(null, id);
			
			@SuppressWarnings("unchecked")
			val table = ((DynamoDbTable<T>) ddbe.table(TABLE_NAME, getSchema(clazz)));
			
			return Optional.ofNullable(table.getItem(key));
		}
		
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
		
		if (!results.hasNext()) return Optional.empty();
		
		T head = schema.mapToItem(results.next());
		
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

	@SneakyThrows
	private <T extends Persistable> void copyFields(Class<T> clazz, BeanTableSchema<T> tableSchema,  T head, String attr, AttributeValue rawValue) {
		val getter = clazz.getMethod("get" + StringUtils.capitalize(attr));
		
		Object convertedVal;
		
		convertedVal = tableSchema.converterForAttribute(attr).transformTo(rawValue);
		
		val setter = clazz.getMethod("set" + StringUtils.capitalize(attr), getter.getReturnType());
		setter.invoke(head, convertedVal);
	}
	
	@Override
	public <T extends Persistable> PaginatedList<T> query(Class<T> clazz)
	{
		return query(clazz, -1, null, null, null, null);
	}
	
	private String fieldForIndex(String index) {
		if (index.equals(Persistable.OBJECT_BY_DATE_INDEX)) {
			return "date";
		} else if (index.equals(Persistable.OBJECT_BY_RATING_INDEX)) {
			return "rating";
		} else if (index.equals(Persistable.OBJECT_BY_LOCATION_INDEX)) {
			return "location";
		} else {
			throw new UnsupportedOperationException();
		}
	}
	
	private String readValue(String index, AttributeValue av) {
		if (index.equals(Persistable.OBJECT_BY_DATE_INDEX)) {
			return av.s();
		} else if (index.equals(Persistable.OBJECT_BY_RATING_INDEX)) {
			return av.n();
		} else if (index.equals(Persistable.OBJECT_BY_LOCATION_INDEX)) {
			return av.s();
		} else {
			throw new UnsupportedOperationException();
		}
	}
	
	@SneakyThrows
	public <T extends Persistable> PaginatedList<T> query(Class<T> clazz, int pageSize, String index, Boolean ascending, String exclusiveStartKey, String sortKey)
	{
		if (StringUtils.isBlank(index)) index = Persistable.OBJECT_BY_DATE_INDEX;
		if (ascending == null) ascending = Boolean.TRUE;
		val field = fieldForIndex(index);
		
		@SuppressWarnings("unchecked")
		val table = ((DynamoDbTable<T>) ddbe.table(TABLE_NAME, TableSchema.fromBean(clazz))).index(index);
		
		val idClassPrefix =(String) clazz.getField("ID_CLASS_PREFIX").get(null);
		
		QueryConditional condition;
		if (sortKey == null) {
			condition = QueryConditional.keyEqualTo(Key.builder().partitionValue(idClassPrefix).build());
		} else {
			condition = QueryConditional.sortBeginsWith(Key.builder().partitionValue(idClassPrefix).sortValue(sortKey).build());
		}
		
		val request = QueryEnhancedRequest.builder()
				.queryConditional(condition);
		
//		if (pageSize != -1) {
//			request.limit(pageSize);
//		}
		if (exclusiveStartKey != null) {
			HashMap<String,AttributeValue> map = new HashMap<String,AttributeValue>();
			
			map.put("idClassPrefix", AttributeValue.fromS(idClassPrefix));
			map.put("page", AttributeValue.fromS(HEAD_PAGE));
			map.put("id", AttributeValue.fromS(exclusiveStartKey.split("~`~")[0]));
			
			if (index.equals(Persistable.OBJECT_BY_DATE_INDEX)) {
				map.put(fieldForIndex(index), AttributeValue.fromS(exclusiveStartKey.split("~`~")[1]));
			} else if (index.equals(Persistable.OBJECT_BY_RATING_INDEX)) {
				map.put(fieldForIndex(index), AttributeValue.fromN(exclusiveStartKey.split("~`~")[1]));
			} else if (index.equals(Persistable.OBJECT_BY_LOCATION_INDEX)) {
				map.put(fieldForIndex(index), AttributeValue.fromS(exclusiveStartKey.split("~`~")[1]));
			}
			
			request.exclusiveStartKey(map);
		}
		request.scanIndexForward(ascending);
		
		var pageIt = table.query(request.build()).iterator();
		
		List<T> results = new ArrayList<T>();
		
		String lastEvaluatedKey = null;
		
		while (pageIt.hasNext() && results.size() < pageSize) {
			val page = pageIt.next();
			
			results.addAll(page.items());
			
			lastEvaluatedKey = page.lastEvaluatedKey() == null ? null : readValue(index, page.lastEvaluatedKey().get(field));
		}
		
		return new PaginatedList<T>(results.stream().limit(pageSize).toList(), pageSize, exclusiveStartKey, lastEvaluatedKey);
	}

	@Override
	public <T extends Persistable> boolean exists(String id, Class<T> clazz) {
		return get(id, clazz).isPresent();
	}
}
