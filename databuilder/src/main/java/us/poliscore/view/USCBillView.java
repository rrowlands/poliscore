package us.poliscore.view;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import software.amazon.awssdk.utils.StringUtils;
import us.poliscore.model.bill.Bill.BillSponsor;

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
	
	protected String popular_title;
	
	protected String url;
	
//	protected LocalDateTime updated_at;
	
	protected LocalDate introduced_at;
	
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
		
		public BillSponsor convert()
		{
			return new BillSponsor(bioguide_id, name);
		}
		
	}

	@JsonIgnore
	public String getBillName() {
		if (StringUtils.isNotBlank(getPopular_title())) return getPopular_title();
		
		if (StringUtils.isNotBlank(getShort_title())) return getShort_title();
		
		if (StringUtils.isNotBlank(getOfficial_title()) && getOfficial_title().length() <= 50) return getOfficial_title();
		
		return getBill_type().toUpperCase() + "-" + getNumber();
	}
	
}
