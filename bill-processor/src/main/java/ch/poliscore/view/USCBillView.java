package ch.poliscore.view;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class USCBillView {
	
	protected String bill_id;
	
	protected String short_title;
	
	protected String bill_type;
	
	protected String number;
	
	protected String congress;
	
//	protected JsonNode actions;
	
//	protected String enacted_as;
	
	protected String official_title;
	
	protected String url;
	
	protected Date updated_at;
	
	protected USCBillSponsor sponsor;
	
	protected List<USCBillSponsor> cosponsors;
	
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class USCBillSponsor {
		
		protected String bioguide_id;
		
		protected String district;
		
		protected String name;
		
		protected String state;
		
		protected String title;
		
		protected String type;
		
	}
	
}
