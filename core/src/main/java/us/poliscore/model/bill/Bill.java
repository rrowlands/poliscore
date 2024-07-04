package us.poliscore.model.bill;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Persistable;

@Data
@DynamoDbBean
@RegisterForReflection
public class Bill implements Persistable {
	
	public static final String ID_CLASS_PREFIX = "BIL";
	
	@JsonIgnore
	protected transient BillText text;
	
	protected LegislativeNamespace namespace = LegislativeNamespace.US_CONGRESS;
	
	protected int congress;
	
	protected BillType type;
	
	protected int number;
	
	protected String name;
	
//	protected String statusUrl;
	
//	protected String textUrl;
	
	protected BillSponsor sponsor;
	
	protected List<BillSponsor> cosponsors;
	
	protected LocalDate introducedDate;
	
//	protected LegislativeChamber originatingChamber;
	
	protected BillInterpretation interpretation;
	
	@DynamoDbIgnore
	@JsonIgnore
	public BillText getText()
	{
		return text;
	}
	
	@JsonIgnore
	@DynamoDbIgnore
	public String getPoliscoreId()
	{
		return generateId(congress, type, number);
	}
	
	public String getUSCId()
	{
		return type.getName().toLowerCase() + number + "-" + congress;
	}
	
	@DynamoDbPartitionKey
	public String getId()
	{
		return getPoliscoreId();
	}
	
	public void setId(String id) { }
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX }) public String getIdClassPrefix() { return ID_CLASS_PREFIX; }
	@Override @JsonIgnore public void setIdClassPrefix(String prefix) { }
	
	@Override @JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX }) public LocalDate getDate() { return introducedDate; }
	@Override @JsonIgnore public void setDate(LocalDate date) { introducedDate = date; }
	
	@Override @JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_RATING_INDEX }) public int getRating() { return interpretation.getRating(); }
	@Override @JsonIgnore public void setRating(int rating) { }
	
	public static String generateId(int congress, BillType type, int number)
	{
		return ID_CLASS_PREFIX + "/" + LegislativeNamespace.US_CONGRESS.getNamespace() + "/" + congress + "/" + type.getName().toLowerCase() + "/" + number;
	}
	
	@Data
	@DynamoDbBean
	@AllArgsConstructor
	@NoArgsConstructor
	public static class BillSponsor {
		
		protected String bioguide_id;
		
		protected String name;
		
	}
}
