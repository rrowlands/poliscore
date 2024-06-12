package ch.poliscore.view;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class USCRollCallData {
	
	protected USCRollCallBillView bill;
	
	protected String category;
	
	protected String chamber;
	
	protected Date date;
	
	protected String question;
	
	protected String result;
	
	protected String source_url;
	
	protected String vote_id;
	
	protected USCRollCallVotes votes;
	
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class USCRollCallBillView {
		
		protected Integer congress;
		
		protected Integer number;
		
		protected String type;
		
	}
	
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class USCRollCallVotes {
		
		protected List<USCRollCallVote> Aye;
		
		protected List<USCRollCallVote> No;
		
		@JsonProperty("Not Voting")
		protected List<USCRollCallVote> NotVoting;
		
		protected List<USCRollCallVote> Present;
		
	}
	
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class USCRollCallVote {
		
		protected String display_name;
		
		protected String id;
		
		protected String party;
		
		protected String state;
		
	}
	
}
