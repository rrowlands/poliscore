package ch.poliscore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.poliscore.interpretation.OpenAIInterpretationMetadata;
import ch.poliscore.interpretation.OpenAISliceInterpretationMetadata;
import lombok.Data;
import lombok.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@DynamoDbBean
public class BillInterpretation implements Persistable
{
	@JsonIgnore
	protected transient Bill bill;
	
	@JsonIgnore
	protected IssueStats issueStats = null;
	
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
	protected OpenAIInterpretationMetadata metadata;
	
	public BillInterpretation()
	{
		
	}
	
	public BillInterpretation(OpenAIInterpretationMetadata metadata, Bill bill, String text)
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
		if (metadata instanceof OpenAISliceInterpretationMetadata)
		{
			return bill.getName() + "-" + ((OpenAISliceInterpretationMetadata)metadata).getSliceIndex();
		}
		else
		{
			return bill.getName();
		}
	}
	
	public static String generateId(String billId, Integer sliceIndex)
	{
		var id = billId;
		
		if (sliceIndex != null)
		{
			id += "-" + sliceIndex;
		}
		
		return id;
	}
}
