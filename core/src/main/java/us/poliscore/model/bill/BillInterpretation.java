package us.poliscore.model.bill;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import us.poliscore.model.AIInterpretationMetadata;
import us.poliscore.model.AISliceInterpretationMetadata;
import us.poliscore.model.IssueStats;
import us.poliscore.model.Persistable;

@Data
@DynamoDbBean
@RegisterForReflection
public class BillInterpretation implements Persistable
{
	public static final String ID_CLASS_PREFIX = "BIT";
	
	@JsonIgnore
	protected transient Bill bill;
	
	@JsonIgnore
	@Getter(onMethod_ = {@DynamoDbIgnore})
	protected transient IssueStats issueStats = null;
	
	@NonNull
	protected String id;
	
	@NonNull
	protected String billId;
	
	/**
	 * The actual interpretation text of the bill or bill slice, as produced by AI.
	 */
	@NonNull
	protected String text;
	
	@NonNull
	protected AIInterpretationMetadata metadata;
	
	public BillInterpretation()
	{
		
	}
	
	public BillInterpretation(AIInterpretationMetadata metadata, Bill bill, String text)
	{
		this.metadata = metadata;
		this.bill = bill;
		this.billId = bill.getId();
		this.text = text;
		this.issueStats = IssueStats.parse(text);
	}
	
	@DynamoDbPartitionKey
	public String getId()
	{
		return this.id;
	}
	
	public void setText(String text)
	{
		this.text = text;
		this.issueStats = IssueStats.parse(text);
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
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_CLASS_INDEX }) public String getIdClassPrefix() { return ID_CLASS_PREFIX; }
	
	@Override @JsonIgnore public void setIdClassPrefix(String prefix) { }
	
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
