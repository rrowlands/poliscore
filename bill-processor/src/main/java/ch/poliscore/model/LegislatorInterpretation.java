package ch.poliscore.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.poliscore.interpretation.OpenAIInterpretationMetadata;
import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@DynamoDbBean
public class LegislatorInterpretation implements Persistable
{
	@JsonIgnore
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
		return legislatorId;
	}
}
