package us.poliscore.model;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import us.poliscore.model.LegislatorBillInteraction.LegislatorBillCosponsor;
import us.poliscore.model.LegislatorBillInteraction.LegislatorBillSponsor;
import us.poliscore.model.LegislatorBillInteraction.LegislatorBillVote;
import us.poliscore.model.bill.Bill;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = LegislatorBillVote.class, name = "LegislatorBillVote"),
    @JsonSubTypes.Type(value = LegislatorBillSponsor.class, name = "LegislatorBillSponsor"),
	@JsonSubTypes.Type(value = LegislatorBillCosponsor.class, name = "LegislatorBillCosponsor") }
)
@Data
@RequiredArgsConstructor
@AllArgsConstructor
@NoArgsConstructor
@RegisterForReflection
public abstract class LegislatorBillInteraction {
	
	public static final String ID_CLASS_PREFIX = "LBI";
	
	@NonNull
	protected String billId;
	
	@NonNull
	protected String billName;
	
//	@NonNull
//	protected String billDescription;
	
	@EqualsAndHashCode.Exclude
	protected IssueStats issueStats;
	
	@NonNull
	@EqualsAndHashCode.Exclude
	protected LocalDate date;
	
	@JsonIgnore
	@DynamoDbIgnore
	abstract public float getJudgementWeight();
	
	@JsonIgnore
	@DynamoDbIgnore
	abstract public String describe();
	
	public boolean supercedes(LegislatorBillInteraction similar)
	{
		return this.equals(similar) && date.isAfter(similar.getDate());
	}
	
	public String generateId(String billId) { return billId.replace(Bill.ID_CLASS_PREFIX, ID_CLASS_PREFIX); }
	
	@Data
	@EqualsAndHashCode(callSuper=true)
	@RegisterForReflection
	@AllArgsConstructor
	@NoArgsConstructor
	public static class LegislatorBillVote extends LegislatorBillInteraction {
		
		@NonNull
		protected VoteStatus voteStatus;
		
		@DynamoDbIgnore
		@JsonIgnore
		public float getJudgementWeight() { return voteStatus.equals(VoteStatus.NAY) ? -0.5f : 0.5f; }
		
		public String describe() { return "Voted " + voteStatus.describe(); }
		
	}
	
	@Data
	@EqualsAndHashCode(callSuper=true)
	@RegisterForReflection
	@NoArgsConstructor
	public static class LegislatorBillSponsor extends LegislatorBillInteraction {
		
		@DynamoDbIgnore
		@JsonIgnore
		public float getJudgementWeight() { return 1.0f; }
		
		public String describe() { return "Sponsor"; }
		
	}
	
	@Data
	@EqualsAndHashCode(callSuper=true)
	@RegisterForReflection
	@NoArgsConstructor
	public static class LegislatorBillCosponsor extends LegislatorBillInteraction {
		
		@DynamoDbIgnore
		@JsonIgnore
		public float getJudgementWeight() { return 0.7f; }
		
		public String describe() { return "Cosponsor"; }
		
	}
	
}