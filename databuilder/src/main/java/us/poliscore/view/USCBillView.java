package us.poliscore.view;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import software.amazon.awssdk.utils.StringUtils;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.bill.Bill.BillSponsor;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.Legislator.LegislatorName;
import us.poliscore.service.storage.MemoryObjectService;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class USCBillView {
	
	protected String bill_id;
	
	protected String short_title;
	
	protected String bill_type;
	
	protected String number;
	
	protected String congress;
	
	protected String status;
	
//	protected JsonNode actions;
	
//	protected String enacted_as;
	
	protected String official_title;
	
	protected String popular_title;
	
	protected String url;
	
//	protected LocalDateTime updated_at;
	
	protected LocalDate introduced_at;
	
	protected USCBillSponsor sponsor;
	
	protected List<USCBillSponsor> cosponsors;
	
	private List<Action> actions;
	
	@JsonIgnore
	public LocalDate getLastActionDate() {
	    if (actions == null || actions.isEmpty()) return null;

	    return actions.stream()
	                  .map(Action::getActedAt)
	                  .filter(Objects::nonNull)
	                  .map(OffsetDateTime::toLocalDate)
	                  .max(LocalDate::compareTo)
	                  .orElse(null);
	}

	
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Action {
	    private String acted_at_raw;
	    private String text;
	    private String type;
	    
	    @JsonIgnore
		public OffsetDateTime getActedAt() {
		    if (acted_at_raw == null) return null;

		    try {
		        // First try parsing full datetime
		        return OffsetDateTime.parse(acted_at_raw);
		    } catch (DateTimeParseException e) {
		        try {
		            // Fallback: plain date
		            LocalDate date = LocalDate.parse(acted_at_raw);
		            return date.atStartOfDay().atOffset(ZoneOffset.UTC);
		        } catch (DateTimeParseException e2) {
		            return null;
		        }
		    }
		}
	}
	
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class USCBillSponsor {
		
		protected String bioguide_id;
		
		protected String district;
		
		protected String name;
		
		protected String state;
		
		protected String title;
		
		protected String type;
		
		public BillSponsor convert(String session, MemoryObjectService memService)
		{
			var legId = Legislator.generateId(LegislativeNamespace.US_CONGRESS, session, bioguide_id);
			var leg = memService.get(legId, Legislator.class).get();
			
			var sponsor = new BillSponsor(legId, leg.getName());
			sponsor.setParty(leg.getParty());
			return sponsor;
		}
		
	}

	@JsonIgnore
	public String getBillName() {
		if (StringUtils.isNotBlank(getPopular_title())) return getPopular_title();
		
		if (StringUtils.isNotBlank(getShort_title())) return getShort_title();
		
		if (StringUtils.isNotBlank(getOfficial_title()) && getOfficial_title().length() <= 200) return getOfficial_title();
		
		return getBill_type().toUpperCase() + "-" + getNumber();
	}
	
}
