package ch.poliscore.model;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.poliscore.interpretation.BillType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@DynamoDbBean
public class Bill implements Persistable {
	
	@JsonIgnore
	private BillText text;
	
	private LegislativeNamespace namespace = LegislativeNamespace.US_CONGRESS;
	
	private int congress;
	
	private BillType type;
	
	private int number;
	
	private String name;
	
	private String statusUrl;
	
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
	
	@JsonIgnore
	public String getTextUrl()
	{
		return "";
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
	
	public static String generateId(int congress, BillType type, int number)
	{
		return LegislativeNamespace.US_CONGRESS.getNamespace() + "/" + congress + "/" + type.getName().toLowerCase() + "/" + number;
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
