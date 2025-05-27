
package us.poliscore.view;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegiscanResponse<T> {
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("alert")
    private LegiscanAlert alert;
    
    @JsonProperty("bill")
    private T bill;
    
    @JsonProperty("person")
    private T person;
    
    @JsonProperty("rollcall")
    private T rollcall;
    
    @JsonProperty("bills")
    private T bills;
    
    @JsonProperty("people")
    private T people;
    
    @JsonProperty("rollcalls")
    private T rollcalls;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LegiscanAlert {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("message")
        private String message;
    }
}
