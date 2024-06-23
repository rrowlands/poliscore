package ch.poliscore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.poliscore.interpretation.OpenAIInterpretationMetadata;
import ch.poliscore.service.storage.DynamoDBPersistenceService;
import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

@Data
@DynamoDbBean
public class LegislatorInterpretation implements Persistable
{
	public static final String ID_CLASS_PREFIX = "LIT";
	
	@JsonIgnore
	@Getter(onMethod_ = {@DynamoDbIgnore})
	protected transient Legislator legislator;
	
	protected IssueStats issueStats;
	
	protected String legislatorId;
	
	protected OpenAIInterpretationMetadata metadata;
	
	public LegislatorInterpretation()
	{
		
	}
	
	public LegislatorInterpretation(OpenAIInterpretationMetadata metadata, Legislator legislator, IssueStats stats)
	{
		this.metadata = metadata;
		this.legislator = legislator;
		this.legislatorId = legislator.getId();
		this.issueStats = stats;
	}
	
	public void setLegislator(Legislator legislator)
	{
		this.legislator = legislator;
		legislatorId = legislator.getId();
	}
	
	@JsonIgnore
	@DynamoDbPartitionKey
	public String getId()
	{
		return generateId(legislatorId);
	}
	
	public void setId(String id) { this.legislatorId = id; }
	
	public String generateId(String legislatorId) { return legislatorId.replace(Bill.ID_CLASS_PREFIX, ID_CLASS_PREFIX); }
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { DynamoDBPersistenceService.OBJECT_CLASS_INDEX }) public String getIdClassPrefix() { return ID_CLASS_PREFIX; }
	
	@Override @JsonIgnore public void setIdClassPrefix(String prefix) { }
}
