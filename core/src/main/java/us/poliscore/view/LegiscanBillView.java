
package us.poliscore.view;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegiscanBillView {
    
    @JsonProperty("bill_id")
    private String billId;
    
    @JsonProperty("bill_number")
    private String billNumber;
    
    @JsonProperty("bill_type")
    private String billType;
    
    @JsonProperty("bill_type_id")
    private Integer billTypeId;
    
    @JsonProperty("body")
    private String body;
    
    @JsonProperty("body_id")
    private Integer bodyId;
    
    @JsonProperty("current_body")
    private String currentBody;
    
    @JsonProperty("current_body_id")
    private Integer currentBodyId;
    
    @JsonProperty("session_id")
    private Integer sessionId;
    
    @JsonProperty("session")
    private LegiscanSessionView session;
    
    @JsonProperty("url")
    private String url;
    
    @JsonProperty("state_link")
    private String stateLink;
    
    @JsonProperty("completed")
    private Integer completed;
    
    @JsonProperty("status")
    private Integer status;
    
    @JsonProperty("status_date")
    private String statusDate;
    
    @JsonProperty("progress")
    private List<LegiscanProgressView> progress;
    
    @JsonProperty("state")
    private String state;
    
    @JsonProperty("state_id")
    private Integer stateId;
    
    @JsonProperty("bill_draft_id")
    private String billDraftId;
    
    @JsonProperty("draft_revision")
    private Integer draftRevision;
    
    @JsonProperty("ml_draft_id")
    private String mlDraftId;
    
    @JsonProperty("title")
    private String title;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("pending_committee_id")
    private Integer pendingCommitteeId;
    
    @JsonProperty("committee")
    private LegiscanCommitteeView committee;
    
    @JsonProperty("referral_date")
    private String referralDate;
    
    @JsonProperty("sponsors")
    private List<LegiscanSponsorView> sponsors;
    
    @JsonProperty("sasts")
    private List<LegiscanSastView> sasts;
    
    @JsonProperty("subjects")
    private List<LegiscanSubjectView> subjects;
    
    @JsonProperty("texts")
    private List<LegiscanTextView> texts;
    
    @JsonProperty("votes")
    private List<LegiscanVoteView> votes;
    
    @JsonProperty("amendments")
    private List<LegiscanAmendmentView> amendments;
    
    @JsonProperty("supplements")
    private List<LegiscanSupplementView> supplements;
    
    @JsonProperty("calendar")
    private List<LegiscanCalendarView> calendar;
    
    @JsonProperty("history")
    private List<LegiscanHistoryView> history;
}
