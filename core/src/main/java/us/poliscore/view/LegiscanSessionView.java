
package us.poliscore.view;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegiscanSessionView {
    
    @JsonProperty("session_id")
    private Integer sessionId;
    
    @JsonProperty("state_id")
    private Integer stateId;
    
    @JsonProperty("year_start")
    private Integer yearStart;
    
    @JsonProperty("year_end")
    private Integer yearEnd;
    
    @JsonProperty("prefile")
    private Integer prefile;
    
    @JsonProperty("sine_die")
    private Integer sineDie;
    
    @JsonProperty("prior")
    private Integer prior;
    
    @JsonProperty("special")
    private Integer special;
    
    @JsonProperty("session_tag")
    private String sessionTag;
    
    @JsonProperty("session_title")
    private String sessionTitle;
    
    @JsonProperty("session_name")
    private String sessionName;
}
