package us.poliscore.model.bill;

import java.time.LocalDate; // Already present
import java.time.temporal.ChronoUnit;
import java.util.List;
import io.quarkus.logging.Log; // Added import

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import lombok.EqualsAndHashCode;
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
import us.poliscore.model.legislator.Legislator.LegislatorName;
import us.poliscore.model.press.PressInterpretation;

@Data
@DynamoDbBean
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RegisterForReflection
public class Bill implements Persistable {
	
	public static final String ID_CLASS_PREFIX = "BIL";
	
	public static final Double DEFAULT_IMPACT_LAW_WEIGHT = 100.0d;
	
	/**
	 * An optional grouping mechanism, beyond the ID_CLASS_PREFIX concept, which allows you to group objects of the same class in different
	 * "storage buckets". Really only used in DynamoDb at the moment, and is used for querying on the object indexes with objects that exist
	 * in different congressional sessions.
	 */
	public static String getClassStorageBucket() {
            LegislativeNamespace ns = PoliscoreUtil.currentNamespace != null ? PoliscoreUtil.currentNamespace : LegislativeNamespace.US_CONGRESS; // Default for safety
            Integer sessionNum = PoliscoreUtil.currentSessionNumber;
            if (sessionNum == null) {
                Log.warn("PoliscoreUtil.currentSessionNumber is null in Bill.getClassStorageBucket. This should be set. Falling back to placeholder session 118 for US_CONGRESS or current year for others.");
                sessionNum = ns == LegislativeNamespace.US_CONGRESS ? 118 : LocalDate.now().getYear(); // Placeholder default
            }
            return getClassStorageBucket(ns, sessionNum); // Calls the existing overload
        }
	public static String getClassStorageBucket(LegislativeNamespace namespace, int session)
	{
		return ID_CLASS_PREFIX + "/" + namespace.getNamespace() + "/" + session;
	}
	
	@JsonIgnore
	protected transient BillText text;
	
	protected LegislativeNamespace namespace; // Default assignment removed
	
	protected Integer session;
	
	protected BillType type;
	
	protected BillStatus status;
	
	protected int number; // Remains int
	
	protected String name;
	
	protected String legiscanBillId; // Added field
	protected String billPdfUrl; // Added field
	
//	protected String statusUrl;
	
//	protected String textUrl;
	
	protected BillSponsor sponsor;
	
	protected List<BillSponsor> cosponsors;
	
	protected LocalDate introducedDate;
	
	protected LocalDate lastActionDate;
	
	protected BillInterpretation interpretation;
	
//	protected List<PressInterpretation> pressInterps;
	
//	protected LocalDate lastPressQuery = LocalDate.EPOCH;
	
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
	public String getPoliscoreId() {
            if (this.namespace == null || this.session == null || this.type == null || this.number == 0 /*代表纯数字编号，后续可能需要调整使其能适应 H.R.123 这种编号*/) {
                 // Or log an error and return a placeholder if this state is possible during normal operation
                 throw new IllegalStateException("Namespace, session, type, or number is not properly set for Bill ID generation. Bill Name: " + this.name);
            }
            // The 'number' field is an int. The new generateId expects a billNumberString.
            // We need to decide if Bill.number should become a String or if LegiscanBillView.number (which is a String)
            // will be parsed into an int for Bill.number and potentially a prefix for Bill.type.
            // For now, let's assume Bill.number (int) is the primary number and type handles the prefix.
            return generateId(this.namespace, this.session, this.type, String.valueOf(this.number));
        }
	
	@JsonIgnore
	public String getUSCId()
	{
		return type.getName().toLowerCase() + number + "-" + session;
	}
	
	public String getShortName()
	{
		if (StringUtils.isNotBlank(name) && name.length() < 125) {
			return name;
		} else if (interpretation != null && StringUtils.isNotBlank(interpretation.getGenBillTitle())) {
			return interpretation.getGenBillTitle();
		}
		
		return name;
	}
	
	@DynamoDbPartitionKey
	@EqualsAndHashCode.Include
	public String getId()
	{
		return getPoliscoreId();
	}
	
	public void setId(String id) { }
	
	public boolean isIntroducedInSession(CongressionalSession session) {
		return Integer.valueOf(session.getNumber()).equals(this.session);
	}
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX, Persistable.OBJECT_BY_RATING_ABS_INDEX, Persistable.OBJECT_BY_IMPACT_INDEX, OBJECT_BY_IMPACT_ABS_INDEX, OBJECT_BY_HOT_INDEX })
        public String getStorageBucket() {
            if (StringUtils.isBlank(getId())) {
                return getClassStorageBucket(); // Fallback
            }
            // ID format: "BIL/ns_part1/ns_part2.../session/type/number"
            // We need "BIL/ns_part1/ns_part2.../session"
            // Count slashes: BIL / ns / session / type / number (for us/congress) -> 4 slashes before number
            // BIL / ns1 / ns2 / session / type / number (for us/state/foo) -> 5 slashes before number
            // The common part is up to and including the session.
            // The ID is "BIL/us/congress/118/hr/123" -> ordinalIndexOf(..., "/", 4) -> "BIL/us/congress/118"
            // The ID is "BIL/us/california/2023/ab/456" -> ordinalIndexOf(..., "/", 4) -> "BIL/us/california/2023"
            // This seems correct.
            return getId().substring(0, StringUtils.ordinalIndexOf(getId(), "/", 4));
        }
	@Override @JsonIgnore public void setStorageBucket(String prefix) { }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX }) public LocalDate getDate() {
		if (lastActionDate != null) return lastActionDate;
		
		return introducedDate;
	}
	@JsonIgnore public void setDate(LocalDate date) { introducedDate = date; }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_RATING_INDEX }) public int getRating() { return interpretation.getRating(); }
	@JsonIgnore public void setRating(int rating) { }
	@JsonIgnore public int getRating(TrackedIssue issue) { return interpretation.getRating(issue); }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_RATING_ABS_INDEX }) public int getRatingAbs() { return Math.abs(interpretation.getRating()); }
	@JsonIgnore public void setRatingAbs(int rating) { }
	
	@DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_IMPACT_INDEX }) public int getImpact() { return getImpact(TrackedIssue.OverallBenefitToSociety); }
	public void setImpact(int impact) { }
	
	@DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_IMPACT_ABS_INDEX }) public int getImpactAbs() { return Math.abs(getImpact()); }
	@JsonIgnore public int getImpactAbs(TrackedIssue issue, double lawWeight) { return Math.abs(getImpact(issue, lawWeight)); }
	public void setImpactAbs(int impact) { }
	
	@DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_HOT_INDEX }) public int getHot() { return (int)(getImpactAbs(TrackedIssue.OverallBenefitToSociety, 1.5d) * Math.exp(-0.02 * ChronoUnit.DAYS.between(getDate(), LocalDate.now()))); }
	public void setHot(int hot) { }
	
	public static String generateId(LegislativeNamespace ns, int session, BillType type, String billNumberString) {
            if (ns == null || type == null || StringUtils.isBlank(billNumberString)) {
                throw new IllegalArgumentException("Namespace, session, type, and billNumberString are required for generating Bill ID.");
            }
            // Normalize billNumberString: remove non-alphanumeric characters to keep it clean if it contains them (e.g. "A.B. 123")
            String cleanBillNumber = billNumberString.replaceAll("[^a-zA-Z0-9]", "");
            return ID_CLASS_PREFIX + "/" + ns.getNamespace() + "/" + session + "/" + type.getName().toLowerCase() + "/" + cleanBillNumber;
        }
	
	@JsonIgnore public int getImpact(TrackedIssue issue) { return getImpact(issue, DEFAULT_IMPACT_LAW_WEIGHT); };
	
	@JsonIgnore public int getImpact(TrackedIssue issue, double lawWeight)
	{
		return calculateImpact(interpretation.getIssueStats().getStat(issue), status.getProgress(), getCosponsorPercent(), lawWeight);
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
                String[] parts = poliscoreId.split("/");
                if (parts.length < 2) throw new IllegalArgumentException("Invalid Poliscore Bill ID for type extraction: " + poliscoreId);
                return BillType.fromName(parts[parts.length - 2]);
            }
	
	public static int billNumberFromId(String poliscoreId) {
                String[] parts = poliscoreId.split("/");
                if (parts.length < 1) throw new IllegalArgumentException("Invalid Poliscore Bill ID for number extraction: " + poliscoreId);
                // The number part might contain non-numeric characters if generateId's cleanBillNumber wasn't aggressive enough
                // or if Bill.number was changed to String. For now, assume it's convertible to int.
                String numberStr = parts[parts.length - 1];
                try {
                    return Integer.parseInt(numberStr.replaceAll("[^0-9]", "")); // Attempt to get only digits
                } catch (NumberFormatException e) {
                    Log.error("Could not parse bill number from ID's last part: " + numberStr + " (original ID: " + poliscoreId + ")", e);
                    return 0; // Or throw
                }
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
		protected LegislatorName name;
		
		@JsonIgnore
		public String getId() {
			return legislatorId;
		}
		
	}
	
	@Data
	@DynamoDbBean
	@RequiredArgsConstructor
	@NoArgsConstructor
	public static class BillSponsorOld {
		
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
