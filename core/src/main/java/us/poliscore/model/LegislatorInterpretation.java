package us.poliscore.model;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

@Data
@DynamoDbBean
@RegisterForReflection
public class LegislatorInterpretation implements Persistable
{
	public static final String ID_CLASS_PREFIX = "LIT";
	
	@JsonIgnore
	@Getter(onMethod_ = {@DynamoDbIgnore})
	protected transient Legislator legislator;
	
	protected IssueStats issueStats;
	
	protected String legislatorId;
	
	protected AIInterpretationMetadata metadata;
	
	protected int hash;
	
	public LegislatorInterpretation()
	{
		
	}
	
	public LegislatorInterpretation(AIInterpretationMetadata metadata, Legislator legislator, IssueStats stats)
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
	
	public static String generateId(String legislatorId) { return legislatorId.replace(Legislator.ID_CLASS_PREFIX, ID_CLASS_PREFIX); }
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX }) public String getIdClassPrefix() { return ID_CLASS_PREFIX; }
	
	@Override @JsonIgnore public void setIdClassPrefix(String prefix) { }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX }) public LocalDate getDate() { return metadata.getDate(); }

	@JsonIgnore public void setDate(LocalDate date) { }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_RATING_INDEX }) public int getRating() { return issueStats.getRating(); }

	@JsonIgnore public void setRating(int rating) { }
}
