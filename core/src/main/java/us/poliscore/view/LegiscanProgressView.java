
package us.poliscore.view;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegiscanProgressView {
    
    @JsonProperty("date")
    private String date;
    
    @JsonProperty("event")
    private Integer event;
    
    @JsonProperty("event_text")
    private String eventText;
}
