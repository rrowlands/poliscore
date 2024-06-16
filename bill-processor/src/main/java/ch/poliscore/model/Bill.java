package ch.poliscore.model;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.poliscore.interpretation.BillType;
import ch.poliscore.view.USCBillView.USCBillSponsor;
import lombok.Data;

@Data
public class Bill implements Persistable {
	@JsonIgnore
	private String text;
	
	private int congress;
	
	private BillType type;
	
	private int number;
	
	private String name;
	
	private String statusUrl;
	
//	private String textUrl;
	
	private USCBillSponsor sponsor;
	
	private List<USCBillSponsor> cosponsors;
	
	private Date introducedDate;
	
//	private LegislativeChamber originatingChamber;
	
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
	
	public String getId()
	{
		return getPoliscoreId();
	}
	
	public static String generateId(int congress, BillType type, int number)
	{
		return congress + "/" + type.getName().toLowerCase() + "/" + number;
	}
}
