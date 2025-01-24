package us.poliscore.model.bill;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Persistable;

@Data
@DynamoDbBean
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class CBOBillAnalysis implements Persistable
{
	public static final String ID_CLASS_PREFIX = "CBO";
	
	protected String summary;
	
	@NonNull
	protected String billId;
	
	public void setId(String id) {}
	
	@DynamoDbPartitionKey
	public String getId()
	{
		return generateId(billId);
	}
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX }) public String getStorageBucket() { return ID_CLASS_PREFIX; }
	@Override @JsonIgnore public void setStorageBucket(String prefix) { }
	
	public static String generateId(String billId) {
		return billId.replace(Bill.ID_CLASS_PREFIX, ID_CLASS_PREFIX);
	}
}
