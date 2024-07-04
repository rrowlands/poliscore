package us.poliscore.model.bill;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import us.poliscore.model.AIInterpretationMetadata;
import us.poliscore.model.AISliceInterpretationMetadata;
import us.poliscore.model.IssueStats;
import us.poliscore.model.Persistable;

@Data
@DynamoDbBean
@RegisterForReflection
@NoArgsConstructor
@AllArgsConstructor
public class BillInterpretation implements Persistable
{
	public static final String ID_CLASS_PREFIX = "BIT";
	
	@JsonIgnore
	@Getter(onMethod_ = {@DynamoDbIgnore})
	protected transient Bill bill;
	
	protected transient IssueStats issueStats;
	
	@NonNull
	protected String id;
	
	@NonNull
	protected String billId;
	
	@NonNull
	protected AIInterpretationMetadata metadata;
	
	@DynamoDbPartitionKey
	public String getId()
	{
		return this.id;
	}
	
	public void setBill(Bill bill)
	{
		this.bill = bill;
		billId = bill.getId();
	}
	
	@JsonIgnore
	public String getName()
	{
		if (metadata instanceof AISliceInterpretationMetadata)
		{
			return bill.getName() + "-" + ((AISliceInterpretationMetadata)metadata).getSliceIndex();
		}
		else
		{
			return bill.getName();
		}
	}
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX }) public String getIdClassPrefix() { return ID_CLASS_PREFIX; }
	
	@Override @JsonIgnore public void setIdClassPrefix(String prefix) { }
	
	@Override @JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX }) public LocalDate getDate() { return metadata.getDate(); }

	@Override @JsonIgnore public void setDate(LocalDate date) { metadata.setDate(date); }
	
	@Override @JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_RATING_INDEX }) public int getRating() { return issueStats.getRating(); }
	@Override @JsonIgnore public void setRating(int rating) { }
	
	public static String generateId(String billId, Integer sliceIndex)
	{
		var id = billId.replace(Bill.ID_CLASS_PREFIX, ID_CLASS_PREFIX);
		
		if (sliceIndex != null)
		{
			id += "-" + sliceIndex;
		}
		
		return id;
	}
}
