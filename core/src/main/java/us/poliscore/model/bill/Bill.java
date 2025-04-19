package us.poliscore.model.bill;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.LegislativeChamber;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Party;
import us.poliscore.model.Persistable;
import us.poliscore.model.TrackedIssue;

@Data
@DynamoDbBean
@RegisterForReflection
public class Bill implements Persistable {
	
	public static final String ID_CLASS_PREFIX = "BIL";
	
	public static final Double DEFAULT_IMPACT_LAW_WEIGHT = 100.0d;
	
	/**
	 * An optional grouping mechanism, beyond the ID_CLASS_PREFIX concept, which allows you to group objects of the same class in different
	 * "storage buckets". Really only used in DynamoDb at the moment, and is used for querying on the object indexes with objects that exist
	 * in different congressional sessions.
	 */
	public static String getClassStorageBucket()
	{
		return ID_CLASS_PREFIX + "/" + LegislativeNamespace.US_CONGRESS.getNamespace() + "/" + PoliscoreUtil.CURRENT_SESSION.getNumber();
	}
	public static String getClassStorageBucket(LegislativeNamespace namespace, int session)
	{
		return ID_CLASS_PREFIX + "/" + namespace.getNamespace() + "/" + session;
	}
	
	@JsonIgnore
	protected transient BillText text;
	
	protected LegislativeNamespace namespace = LegislativeNamespace.US_CONGRESS;
	
	protected Integer session;
	
	protected BillType type;
	
	protected BillStatus status;
	
	protected int number;
	
	protected String name;
	
//	protected String statusUrl;
	
//	protected String textUrl;
	
	protected BillSponsor sponsor;
	
	protected List<BillSponsor> cosponsors;
	
	protected LocalDate introducedDate;
	
	protected LocalDate lastActionDate;
	
	protected BillInterpretation interpretation;
	
	@JsonIgnore
	protected CBOBillAnalysis cboAnalysis;
	
	public void setInterpretation(BillInterpretation interp) {
		this.interpretation = interp;
		
		if (getName() != null && getName().contains(String.valueOf(getNumber())) && !StringUtils.isBlank(interp.getGenBillTitle())) {
			setName(interp.getGenBillTitle());
		}
	}
	
	@JsonIgnore
	public LegislativeChamber getOriginatingChamber()
	{
		return BillType.getOriginatingChamber(type);
	}
	
	@DynamoDbIgnore
	@JsonIgnore
	public BillText getText()
	{
		return text;
	}
	
	@JsonIgnore
	@DynamoDbIgnore
	public String getPoliscoreId()
	{
		return generateId(session, type, number);
	}
	
	@JsonIgnore
	public String getUSCId()
	{
		return type.getName().toLowerCase() + number + "-" + session;
	}
	
	@DynamoDbPartitionKey
	public String getId()
	{
		return getPoliscoreId();
	}
	
	public void setId(String id) { }
	
	public boolean isIntroducedInSession(CongressionalSession session) {
		return Integer.valueOf(session.getNumber()).equals(this.session);
	}
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX, Persistable.OBJECT_BY_IMPACT_INDEX, OBJECT_BY_IMPACT_ABS_INDEX, OBJECT_BY_HOT_INDEX }) public String getStorageBucket() {
		if (!StringUtils.isEmpty(this.getId()))
			return this.getId().substring(0, StringUtils.ordinalIndexOf(getId(), "/", 4));
		
		return getClassStorageBucket();
	}
	@Override @JsonIgnore public void setStorageBucket(String prefix) { }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX }) public LocalDate getDate() { return introducedDate; }
	@JsonIgnore public void setDate(LocalDate date) { introducedDate = date; }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_RATING_INDEX }) public int getRating() { return interpretation.getRating(); }
	@JsonIgnore public void setRating(int rating) { }
	@JsonIgnore public int getRating(TrackedIssue issue) { return interpretation.getRating(issue); }
	
	@DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_IMPACT_INDEX }) public int getImpact() { return getImpact(TrackedIssue.OverallBenefitToSociety); }
	public void setImpact(int impact) { }
	
	@DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_IMPACT_ABS_INDEX }) public int getImpactAbs() { return Math.abs(getImpact()); }
	@JsonIgnore public int getImpactAbs(TrackedIssue issue, double lawWeight) { return Math.abs(getImpact(issue, lawWeight)); }
	public void setImpactAbs(int impact) { }
	
	@DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_HOT_INDEX }) public int getHot() { return (int)(getImpactAbs(TrackedIssue.OverallBenefitToSociety, 2.0d) * Math.exp(-0.02 * ChronoUnit.DAYS.between(getHotDate(), LocalDate.now()))); }
	public void setHot(int hot) { }
	
	public static String generateId(int congress, BillType type, int number)
	{
		return ID_CLASS_PREFIX + "/" + LegislativeNamespace.US_CONGRESS.getNamespace() + "/" + congress + "/" + type.getName().toLowerCase() + "/" + number;
	}
	
	@JsonIgnore public int getImpact(TrackedIssue issue) { return getImpact(issue, DEFAULT_IMPACT_LAW_WEIGHT); };
	
	@JsonIgnore public int getImpact(TrackedIssue issue, double lawWeight)
	{
		return calculateImpact(interpretation.getIssueStats().getStat(issue), status.getProgress(), getCosponsorPercent(), lawWeight);
	}
	
	private LocalDate getHotDate()
	{
		if (lastActionDate != null) return lastActionDate;
		
		return introducedDate;
	}

	public static int calculateImpact(int rating, float statusProgress, float cosponsorPercent)
	{
		// 100 is the default 'lawWeight' for impact, and this is because when it comes to legislators, we want the legislator with the most sponsored
		// laws to massively outweigh a legislator that otherwise just voted on the most bills. There is one specific scenario where we want the weight
		// to be calculated differently, however, and that is when calculating the bill 'hot' index. In that scenario, we want laws to be important, but
		// not always outweigh everything else, as we want the date to be a factor which sometimes outweighs the law weight.
		return calculateImpact(rating, statusProgress, cosponsorPercent, DEFAULT_IMPACT_LAW_WEIGHT);
	}
	
	public static int calculateImpact(int rating, float statusProgress, float cosponsorPercent, double lawWeight)
	{
		double statusTerm = statusProgress*100000d * (statusProgress == 1.0f ? lawWeight : 1d);
		double ratingTerm = Math.abs((double)rating/100f)*10000d;
		double cosponsorTerm = cosponsorPercent*1000d;
		int sign = rating < 0 ? -1 : 1;
		
		return (int) Math.round(statusTerm + ratingTerm + cosponsorTerm ) * sign;
	}
	
	/*
	 * A percentage of how much of the chamber has cosponsored the bill. In the house this number is 435. In the senate this is 100.
	 */
	public float getCosponsorPercent()
	{
		float percent;
		
		if (getOriginatingChamber().equals(LegislativeChamber.HOUSE))
		{
			percent = (float)cosponsors.size() / 435f;
		}
		else
		{
			percent = (float)cosponsors.size() / 100f;
		}
		
		return percent;
	}
	
	public static BillType billTypeFromId(String poliscoreId) {
		return BillType.fromName(poliscoreId.split("/")[4]);
	}
	
	public static int billNumberFromId(String poliscoreId) {
		return Integer.valueOf(poliscoreId.split("/")[5]);
	}
	
	@Data
	@DynamoDbBean
	@RequiredArgsConstructor
	@NoArgsConstructor
	public static class BillSponsor {
		
		@JsonIgnore
		@Getter(onMethod = @__({ @DynamoDbIgnore }))
		protected String bioguide_id;
		
		@NonNull
		protected String legislatorId;
		
		protected Party party;
		
		@NonNull
		protected String name;
		
		@JsonIgnore
		public String getId() {
			return legislatorId;
		}
		
	}
}
