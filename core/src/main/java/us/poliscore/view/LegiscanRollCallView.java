
package us.poliscore.view;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegiscanRollCallView {
    
    @JsonProperty("roll_call_id")
    private Integer rollCallId;
    
    @JsonProperty("bill_id")
    private Integer billId;
    
    @JsonProperty("date")
    private String date;
    
    @JsonProperty("desc")
    private String description;
    
    @JsonProperty("yea")
    private Integer yea;
    
    @JsonProperty("nay")
    private Integer nay;
    
    @JsonProperty("nv")
    private Integer nv;
    
    @JsonProperty("absent")
    private Integer absent;
    
    @JsonProperty("total")
    private Integer total;
    
    @JsonProperty("passed")
    private Integer passed;
    
    @JsonProperty("chamber")
    private String chamber;
    
    @JsonProperty("chamber_id")
    private Integer chamberId;
    
    @JsonProperty("votes")
    private List<LegiscanVoteDetailView> votes;
}
