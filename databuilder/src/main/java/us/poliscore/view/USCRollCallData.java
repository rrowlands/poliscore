package us.poliscore.view;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.quarkus.logging.Log;
import lombok.Data;
import lombok.NoArgsConstructor;

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
	@JsonDeserialize(using = USCRollCallVotesDeserializer.class)
	@NoArgsConstructor
	public static class USCRollCallVotes {
	    protected List<USCRollCallVote> affirmative = new ArrayList<>();
	    protected List<USCRollCallVote> negative = new ArrayList<>();
	    protected List<USCRollCallVote> notVoting = new ArrayList<>();
	    protected List<USCRollCallVote> present = new ArrayList<>();

	    public List<USCRollCallVote> getAffirmative() {
	        return affirmative;
	    }

	    public List<USCRollCallVote> getNegative() {
	        return negative;
	    }
	}
	
	@NoArgsConstructor
	public static class USCRollCallVotesDeserializer extends JsonDeserializer<USCRollCallVotes> {
	    @Override
	    public USCRollCallVotes deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
	        ObjectMapper mapper = (ObjectMapper) jp.getCodec();
	        JsonNode root = mapper.readTree(jp);

	        USCRollCallVotes result = new USCRollCallVotes();

	        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
	        while (fields.hasNext()) {
	            Map.Entry<String, JsonNode> entry = fields.next();
	            String key = entry.getKey().toLowerCase(Locale.ROOT);
	            
	            List<USCRollCallVote> votes = new ArrayList<>();
	            for (JsonNode node : entry.getValue()) {
	                if (node.isObject()) {
	                    votes.add(mapper.convertValue(node, USCRollCallVote.class));
	                } else {
	                    // Optionally log or count the bad entry
	                    Log.error("Skipping malformed vote entry: " + node);
	                }
	            }

	            switch (key) {
	                case "aye":
	                case "yea":
	                case "yes":
	                case "for":
	                    result.affirmative.addAll(votes);
	                    break;
	                case "no":
	                case "nay":
	                case "against":
	                    result.negative.addAll(votes);
	                    break;
	                case "not voting":
	                    result.notVoting.addAll(votes);
	                    break;
	                case "present":
	                    result.present.addAll(votes);
	                    break;
	                default:
	                    // ignore unknown
	            }
	        }

	        return result;
	    }
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
//			if (id.length() < 7)
//			{
//				String newId = String.valueOf(id.charAt(0));
//				for (int i = 0; i < 7 - id.length(); ++i) { newId += "0"; }
//				id = newId + id.substring(1);
//			}
			
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
