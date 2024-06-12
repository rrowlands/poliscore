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
	
	private String id;
	
//	private int number;
//	
//	private int session;
	
	private String name;
	
	private String statusUrl;
	
//	private String textUrl;
	
	private String type;
	
	private USCBillSponsor sponsor;
	
	private List<USCBillSponsor> cosponsors;
	
	private Date lastUpdated;
	
//	private LegislativeChamber originatingChamber;
	
	@JsonIgnore
	public String getTextUrl()
	{
		return "";
	}
}
