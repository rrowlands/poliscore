
package us.poliscore.view;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegiscanTextView {
    
    @JsonProperty("doc_id")
    private Integer docId;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("type_id")
    private Integer typeId;
    
    @JsonProperty("mime")
    private String mime;
    
    @JsonProperty("mime_id")
    private Integer mimeId;
    
    @JsonProperty("url")
    private String url;
    
    @JsonProperty("state_link")
    private String stateLink;
    
    @JsonProperty("text_size")
    private Integer textSize;
}
