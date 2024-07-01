package us.poliscore.view;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class USCRollCallData {
	
	protected USCRollCallBillView bill;
	
	protected String category;
	
	protected String chamber;
	
	@JsonDeserialize(using = USCRollCallJsonDateDeserializer.class)
	protected LocalDateTime date;
	
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
		
		@JsonProperty("Aye")
		protected List<USCRollCallVote> Aye = new ArrayList<USCRollCallVote>();
		
		@JsonProperty("No")
		protected List<USCRollCallVote> No = new ArrayList<USCRollCallVote>();
		
		@JsonProperty("Not Voting")
		protected List<USCRollCallVote> NotVoting = new ArrayList<USCRollCallVote>();
		
		@JsonProperty("Present")
		protected List<USCRollCallVote> Present = new ArrayList<USCRollCallVote>();
		
	}
	
	@Data
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class USCRollCallVote {
		
		protected String display_name;
		
		protected String id;
		
		protected String party;
		
		protected String state;
		
		public void setId(String id)
		{
			if (id.length() < 7)
			{
				String newId = String.valueOf(id.charAt(0));
				for (int i = 0; i < 7 - id.length(); ++i) { newId += "0"; }
				id = newId + id.substring(1);
			}
			
			this.id = id;
		}
		
	}
	
	public static class USCRollCallJsonDateDeserializer extends JsonDeserializer<LocalDateTime> {
		
		@Override
	    public LocalDateTime deserialize(JsonParser jsonParser,
	            DeserializationContext deserializationContext) throws IOException, JsonProcessingException {

			// 2024-06-10T08:01:30-04:00
//	        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'-'");
//	        String date = jsonParser.getText();
//	        try {
//	            return format.parse(date);
//	        } catch (ParseException e) {
//	            throw new RuntimeException(e);
//	        }

//			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'-'");
			
			return LocalDateTime.parse(jsonParser.getText(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	    }
		
	}
	
}
