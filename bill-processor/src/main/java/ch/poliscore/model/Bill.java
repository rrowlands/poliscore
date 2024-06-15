package ch.poliscore.model;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import ch.poliscore.view.USCBillView.USCBillSponsor;
import lombok.Data;

@Data
public class Bill implements Persistable {
	@JsonIgnore
	private String text;
	
	private int congress;
	
	private String type;
	
	private int number;
	
	private String name;
	
	private String statusUrl;
	
//	private String textUrl;
	
	private USCBillSponsor sponsor;
	
	private List<USCBillSponsor> cosponsors;
	
	private Date lastUpdated;
	
//	private LegislativeChamber originatingChamber;
	
	@JsonIgnore
	public String getTextUrl()
	{
		return "";
	}
	
	public String getPoliscoreId()
	{
		return congress + "-" + type + "-" + number;
	}
	
	public String getUSCId()
	{
		return type + number + "-" + congress;
	}
	
	public String getId()
	{
		return getPoliscoreId();
	}
}
