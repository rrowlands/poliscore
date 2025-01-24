package us.poliscore.model.bill;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Persistable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public class BillText implements Persistable
{
	public static final String ID_CLASS_PREFIX = "BTX";
	
	@NonNull
	protected String billId;
	
	@NonNull
	protected String xml;
	
	protected LocalDate lastUpdated;
	
	@JsonIgnore
	public String getId()
	{
		return generateId(billId);
	}
	
	public void setId(String id) { this.billId = id; }
	
	public static String generateId(String billId) { return billId.replace(Bill.ID_CLASS_PREFIX, ID_CLASS_PREFIX); }
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX }) public String getStorageBucket() { return ID_CLASS_PREFIX; }
	@Override @JsonIgnore public void setStorageBucket(String prefix) { }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX }) public LocalDate getDate() { return lastUpdated; }
	@JsonIgnore public void setDate(LocalDate date) { lastUpdated = date; }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_RATING_INDEX }) public int getRating() { return 0; }
	@JsonIgnore public void setRating(int rating) { }
	
}
