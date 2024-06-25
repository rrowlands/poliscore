package us.poliscore.model;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import us.poliscore.interpretation.BillType;
import us.poliscore.service.storage.DynamoDBPersistenceService;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

@Data
@DynamoDbBean
@RegisterForReflection
public class Bill implements Persistable {
	
	public static final String ID_CLASS_PREFIX = "BIL";
	
	@JsonIgnore
	private transient BillText text;
	
	private LegislativeNamespace namespace = LegislativeNamespace.US_CONGRESS;
	
	private int congress;
	
	private BillType type;
	
	private int number;
	
	private String name;
	
//	private String statusUrl;
	
//	private String textUrl;
	
	private BillSponsor sponsor;
	
	private List<BillSponsor> cosponsors;
	
	private LocalDate introducedDate;
	
//	private LegislativeChamber originatingChamber;
	
	@DynamoDbIgnore
	@JsonIgnore
	public BillText getText()
	{
		return text;
	}
	
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
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { DynamoDBPersistenceService.OBJECT_CLASS_INDEX }) public String getIdClassPrefix() { return ID_CLASS_PREFIX; }
	@Override @JsonIgnore public void setIdClassPrefix(String prefix) { }
	
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
