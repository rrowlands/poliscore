package us.poliscore.model.legislator;

import java.time.LocalDate;
import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import us.poliscore.model.AIInterpretationMetadata;
import us.poliscore.model.IssueStats;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Persistable;

@Data
@DynamoDbBean
@RegisterForReflection
public class LegislatorInterpretation implements Persistable
{
	public static final String ID_CLASS_PREFIX = "LIT";
	
	protected IssueStats issueStats;
	
	@NonNull
	@Getter(onMethod_ = {@DynamoDbPartitionKey})
	protected String id;
	
	protected AIInterpretationMetadata metadata;
	
	protected int hash;
	
	protected String longExplain;
	
	public LegislatorInterpretation()
	{
		
	}
	
	public LegislatorInterpretation(String legislatorId, Integer session, AIInterpretationMetadata metadata, IssueStats stats)
	{
		this.id = generateId(legislatorId, session);
		this.metadata = metadata;
		this.issueStats = stats;
	}
	
	@JsonIgnore
	@DynamoDbIgnore
	public String getSession()
	{
		return Arrays.asList(this.id.split("/")).get(3);
	}
	
	@JsonIgnore
	@DynamoDbIgnore
	public String getLegislatorId()
	{
		return Legislator.ID_CLASS_PREFIX + "/" + LegislativeNamespace.US_CONGRESS.getNamespace() + "/" + Arrays.asList(this.id.split("/")).getLast();
	}
	
	public static String generateId(String legislatorId, Integer session) { return ID_CLASS_PREFIX + "/" + LegislativeNamespace.US_CONGRESS.getNamespace() + "/" + session + "/" + Arrays.asList(legislatorId.split("/")).getLast(); }
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX }) public String getStorageBucket() { return ID_CLASS_PREFIX; }
	
	@Override @JsonIgnore public void setStorageBucket(String prefix) { }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX }) public LocalDate getDate() { return metadata.getDate(); }

	@JsonIgnore public void setDate(LocalDate date) { }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_RATING_INDEX }) public int getRating() { return issueStats.getRating(); }

	@JsonIgnore public void setRating(int rating) { }
}
