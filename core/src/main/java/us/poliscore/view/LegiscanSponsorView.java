
package us.poliscore.view;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegiscanSponsorView {
    
    @JsonProperty("people_id")
    private Integer peopleId;
    
    @JsonProperty("person_hash")
    private String personHash;
    
    @JsonProperty("party_id")
    private Integer partyId;
    
    @JsonProperty("party")
    private String party;
    
    @JsonProperty("role_id")
    private Integer roleId;
    
    @JsonProperty("role")
    private String role;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("first_name")
    private String firstName;
    
    @JsonProperty("middle_name")
    private String middleName;
    
    @JsonProperty("last_name")
    private String lastName;
    
    @JsonProperty("suffix")
    private String suffix;
    
    @JsonProperty("nickname")
    private String nickname;
    
    @JsonProperty("district")
    private String district;
    
    @JsonProperty("sponsor_type_id")
    private Integer sponsorTypeId;
    
    @JsonProperty("sponsor_order")
    private Integer sponsorOrder;
    
    @JsonProperty("committee_sponsor")
    private Integer committeeSponsor;
    
    @JsonProperty("committee_id")
    private Integer committeeId;
}
