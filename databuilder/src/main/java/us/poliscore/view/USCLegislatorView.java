package us.poliscore.view;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import software.amazon.awssdk.utils.StringUtils;
import us.poliscore.model.Legislator.LegislatorName;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class USCLegislatorView {
	
	protected USCLegislatorId id;
	
	protected USCLegislatorName name;
	
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class USCLegislatorName {
		
		protected String first;
		
		protected String last;
		
		protected String official_full;
		
		public LegislatorName convert()
		{
			if (StringUtils.isBlank(official_full))
				official_full = first + " " + last;
			
			return new LegislatorName(first, last, official_full);
		}
		
	}
	
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class USCLegislatorId {
		
		protected String bioguide;
		
		protected String thomas;
		
		protected String lis;
		
		protected String govtrack;
		
		protected String votesmart;
		
		protected String cspan;
		
		protected String wikipedia;
		
		protected String house_history;
		
		protected String ballotpedia;
		
		protected String maplight;
		
		protected String icpsr;
		
		protected String wikidata;
		
		protected String google_entity_id;
		
	}
	
}