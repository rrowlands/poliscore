package us.poliscore.model;

import java.util.HashSet;
import java.util.Set;

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

@Data
@DynamoDbBean
@NoArgsConstructor
@RegisterForReflection
public class Legislator implements Persistable {
	
	public static final String ID_CLASS_PREFIX = "LEG";
	
	@NonNull
	protected LegislatorName name;
	
	protected String bioguideId;
	
	protected String thomasId;
	
	protected String wikidataId;
	
	protected LegislatorInterpretation interpretation;
	
	@NonNull
	@Getter(onMethod_ = {@DynamoDbIgnore})
	protected Set<LegislatorBillInteration> interactions = new HashSet<LegislatorBillInteration>();
	
	@DynamoDbPartitionKey
	public String getId()
	{
		if (bioguideId != null) return generateId(LegislativeNamespace.US_CONGRESS, bioguideId);
		if (thomasId != null) return generateId(LegislativeNamespace.US_CONGRESS, thomasId);
		
		throw new NullPointerException();
	}
	
	public void setId(String id) { this.bioguideId = id; }
	
	public void addBillInteraction(LegislatorBillInteration incoming)
	{
		interactions.removeIf(existing -> incoming.supercedes(existing));
		interactions.add(incoming);
	}
	
	public static String generateId(LegislativeNamespace ns, String bioguideId)
	{
		return ID_CLASS_PREFIX + "/" + ns.getNamespace() + "/" + bioguideId;
	}
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_CLASS_INDEX }) public String getIdClassPrefix() { return ID_CLASS_PREFIX; }
	
	@Override @JsonIgnore public void setIdClassPrefix(String prefix) { }
	
	@Data
	@DynamoDbBean
	@NoArgsConstructor
	@AllArgsConstructor
	public static class LegislatorName {
		
		protected String first;
		
		protected String last;
		
		protected String official_full;
		
	}
	
}