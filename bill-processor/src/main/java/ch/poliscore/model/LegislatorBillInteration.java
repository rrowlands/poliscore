package ch.poliscore.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import ch.poliscore.VoteStatus;
import ch.poliscore.model.LegislatorBillInteration.LegislatorBillCosponsor;
import ch.poliscore.model.LegislatorBillInteration.LegislatorBillSponsor;
import ch.poliscore.model.LegislatorBillInteration.LegislatorBillVote;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = LegislatorBillVote.class, name = "LegislatorBillVote"),
    @JsonSubTypes.Type(value = LegislatorBillSponsor.class, name = "LegislatorBillSponsor"),
	@JsonSubTypes.Type(value = LegislatorBillCosponsor.class, name = "LegislatorBillCosponsor") }
)
@Data
@RequiredArgsConstructor
@NoArgsConstructor
public abstract class LegislatorBillInteration {
	
	@NonNull
	protected String billId;
	
	@EqualsAndHashCode.Exclude
	protected IssueStats issueStats;
	
	@NonNull
	@EqualsAndHashCode.Exclude
	protected Date date;
	
	@JsonIgnore
	abstract public float getJudgementWeight();
	
	@JsonIgnore
	abstract public String describe();
	
	public boolean supercedes(LegislatorBillInteration similar)
	{
		return this.equals(similar) && date.after(similar.getDate());
	}
	
	@Data
	@EqualsAndHashCode(callSuper=true)
	public static class LegislatorBillVote extends LegislatorBillInteration {
		
		@NonNull
		protected VoteStatus voteStatus;
		
		public float getJudgementWeight() { return voteStatus.equals(VoteStatus.NAY) ? -0.5f : 0.5f; }
		
		public String describe() { return "Voted " + voteStatus.describe(); }
		
	}
	
	@Data
	@EqualsAndHashCode(callSuper=true)
	public static class LegislatorBillSponsor extends LegislatorBillInteration {
		
		public float getJudgementWeight() { return 1.0f; }
		
		public String describe() { return "Sponsor"; }
		
	}
	
	@Data
	@EqualsAndHashCode(callSuper=true)
	public static class LegislatorBillCosponsor extends LegislatorBillInteration {
		
		public float getJudgementWeight() { return 0.7f; }
		
		public String describe() { return "Cosponsor"; }
		
	}
	
}
