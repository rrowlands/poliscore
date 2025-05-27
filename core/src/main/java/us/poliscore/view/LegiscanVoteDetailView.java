
package us.poliscore.view;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegiscanVoteDetailView {
    
    @JsonProperty("people_id")
    private Integer peopleId;
    
    @JsonProperty("vote_id")
    private Integer voteId;
    
    @JsonProperty("vote_text")
    private String voteText;
}
