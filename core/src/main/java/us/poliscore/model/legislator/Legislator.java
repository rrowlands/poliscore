package us.poliscore.model.legislator;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import io.quarkus.logging.Log; // Added import
import java.util.Map;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.val;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
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
import us.poliscore.model.dynamodb.DdbDataPage;
import us.poliscore.model.dynamodb.IssueStatsMapLongAttributeConverter;
import us.poliscore.model.dynamodb.JacksonAttributeConverter.CompressedLegislatorBillInteractionListConverter;
import us.poliscore.model.dynamodb.JacksonAttributeConverter.LegislatorBillInteractionSetConverterProvider;
import us.poliscore.model.dynamodb.JacksonAttributeConverter.LegislatorLegislativeTermSortedSetConverter;

@Data
@DynamoDbBean
@NoArgsConstructor
@RegisterForReflection
public class Legislator implements Persistable, Comparable<Legislator> {
	
	public static final String ID_CLASS_PREFIX = "LEG";
	
	/**
	 * An optional grouping mechanism, beyond the ID_CLASS_PREFIX concept, which allows you to group objects of the same class in different
	 * "storage buckets". Really only used in DynamoDb at the moment, and is used for querying on the object indexes with objects that exist
	 * in different congressional sessions.
	 */
	public static String getClassStorageBucket() {
            LegislativeNamespace ns = PoliscoreUtil.currentNamespace != null ? PoliscoreUtil.currentNamespace : LegislativeNamespace.US_CONGRESS;
            Integer sessionNum = PoliscoreUtil.currentSessionNumber != null ? PoliscoreUtil.currentSessionNumber : (PoliscoreUtil.CURRENT_SESSION != null ? PoliscoreUtil.CURRENT_SESSION.getNumber() : null);
            if (sessionNum == null) {
                 // Attempt to get session from a default if currentSessionNumber isn't set - this might indicate an issue elsewhere
                 // For now, let's log and use a fallback, but this needs to be reliable.
                 Log.warn("PoliscoreUtil.currentSessionNumber is null in Legislator.getClassStorageBucket. Falling back to legacy CURRENT_SESSION or default. This should be set.");
                 // Fallback to a default session or handle error, e.g., 118 for congress if nothing else set
                 // This fallback logic might need to be more robust or removed if currentSessionNumber is guaranteed.
                 sessionNum = 118; // Placeholder default
            }
            return ID_CLASS_PREFIX + "/" + ns.getNamespace() + "/" + sessionNum;
        }
	
	@NonNull
	protected LegislatorName name;
	
	protected Integer session;
	
	protected String bioguideId;
	protected String legiscanId; // Added legiscanId field
	
	protected String thomasId;
	
	// Senate Id : https://github.com/usgpo/bill-status/issues/241
	protected String lisId;
	
	protected String wikidataId;
	
	protected LegislatorInterpretation interpretation;
	
	@NonNull
	protected LocalDate birthday;
	
	protected String photoUrl; // Added photoUrl field
	
	@Getter(onMethod = @__({ @DynamoDbConvertedBy(IssueStatsMapLongAttributeConverter.class) }))
	public Map<TrackedIssue, Long> impactMap = new HashMap<TrackedIssue, Long>();
	
	@NonNull
	@Getter(onMethod = @__({ @DynamoDbConvertedBy(LegislatorLegislativeTermSortedSetConverter.class) }))
	protected LegislatorLegislativeTermSortedSet terms;
	
	private LegislatorBillInteractionList interactionsPrivate1 = new LegislatorBillInteractionList();
	@DdbDataPage
	@DynamoDbConvertedBy(CompressedLegislatorBillInteractionListConverter.class)
	@JsonIgnore
	public LegislatorBillInteractionList getInteractionsPrivate1() {
		return interactionsPrivate1;
	}
	private LegislatorBillInteractionList interactionsPrivate2 = new LegislatorBillInteractionList();
	@DdbDataPage("2")
	@DynamoDbConvertedBy(CompressedLegislatorBillInteractionListConverter.class)
	@JsonIgnore
	public LegislatorBillInteractionList getInteractionsPrivate2() {
		return interactionsPrivate2;
	}
	
	@JsonProperty
	public LegislatorBillInteractionList getInteractions()
	{
		var result = new LegislatorBillInteractionList();
		result.addAll(interactionsPrivate1);
		result.addAll(interactionsPrivate2);
		return result;
	}
	
	@JsonProperty
	public void setInteractions(LegislatorBillInteractionList list)
	{
		interactionsPrivate1 = new LegislatorBillInteractionList();
		interactionsPrivate1.addAll(list.subList(0, Math.min(1000, list.size())));
		
		interactionsPrivate2 = new LegislatorBillInteractionList();
		if (list.size() > 1000)
			interactionsPrivate2.addAll(list.subList(1000, list.size()));
	}
	
	@DynamoDbPartitionKey
	public String getId() {
            if (this.session == null) {
                // Or handle this error more gracefully
                throw new IllegalStateException("Session cannot be null when generating ID for Legislator with bioguideId: " + bioguideId + " and legiscanId: " + legiscanId);
            }
            LegislativeNamespace ns = PoliscoreUtil.currentNamespace != null ? PoliscoreUtil.currentNamespace : LegislativeNamespace.US_CONGRESS; // Default for safety, but should be set
            if (ns != LegislativeNamespace.US_CONGRESS && !StringUtils.isBlank(this.legiscanId)) {
                return generateId(ns, this.session, this.legiscanId, true);
            }
            if (!StringUtils.isBlank(this.bioguideId)) {
                return generateId(ns, this.session, this.bioguideId, false);
            }
            if (!StringUtils.isBlank(this.thomasId)) { // Fallback for older data if bioguide is missing for US_CONGRESS
                return generateId(LegislativeNamespace.US_CONGRESS, this.session, this.thomasId, false);
            }
            if (!StringUtils.isBlank(this.legiscanId)) { // Fallback if everything else is missing
                return generateId(ns, this.session, this.legiscanId, true);
            }
            throw new NullPointerException("Cannot generate ID: bioguideId, thomasId, and legiscanId are all blank.");
        }
	
	public void setId(String id) { }
	
	public void addBillInteraction(LegislatorBillInteraction incoming)
	{
		var interactions = interactionsPrivate1;
		if (interactions.size() >= 1000)
		{
			interactions = interactionsPrivate2;
		}
		
		interactions.removeIf(existing -> incoming.supercedes(existing));
		
		if (!interactions.contains(incoming)) {
			interactions.add(incoming);
		}
	}
	
	@JsonIgnore
	@DynamoDbIgnore
	public Party getParty()
	{
		return this.terms.last().getParty();
	}
	
	public void setBirthday(LocalDate date) {
		if (date == null)
			date = LocalDate.of(1970, 1, 1);
		
		this.birthday = date;
	}
	
	@JsonIgnore
	@DynamoDbIgnore
	public LegislativeTerm getCurrentTerm()
	{
		if (this.terms == null || this.terms.size() == 0) return null;
		
		val session = CongressionalSession.of(this.session);
		
		return this.terms.stream().filter(t -> t.getStartDate().isBefore(session.getEndDate()) && t.getEndDate().isAfter(session.getStartDate())).findFirst().orElse(null);
	}
	
	public boolean isMemberOfSession(CongressionalSession session) {
		if (this.terms == null || this.terms.size() == 0) return false;
		
		return this.terms.stream().anyMatch(t -> t.getStartDate().isBefore(session.getEndDate()) && t.getEndDate().isAfter(session.getStartDate()));
		
//		return Integer.valueOf(session.getNumber()).equals(this.session);
	}
	
	private static String generateId(LegislativeNamespace ns, Integer session, String nativeId, boolean isLegiscanId) {
            if (ns == null || session == null || StringUtils.isBlank(nativeId)) {
                throw new IllegalArgumentException("Namespace, session, and nativeId are required for generating ID.");
            }
            // Potentially add a prefix to nativeId if isLegiscanId to avoid collision, or ensure Legiscan IDs are distinct
            return ID_CLASS_PREFIX + "/" + ns.getNamespace() + "/" + session.toString() + "/" + nativeId;
        }

	public static String generateId(LegislativeNamespace ns, Integer session, String bioguideId) {
            return generateId(ns, session, bioguideId, false);
        }

	public static String generateId(LegislativeNamespace ns, String session, String bioguideId) {
            // Assuming session here is always an Integer convertible string for PoliscoreUtil.currentSessionNumber consistency
            return generateId(ns, Integer.valueOf(session), bioguideId, false);
        }

	public static String generateIdForLegiscan(LegislativeNamespace ns, Integer session, String legiscanId) {
            return generateId(ns, session, legiscanId, true);
        }
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX, Persistable.OBJECT_BY_RATING_ABS_INDEX, Persistable.OBJECT_BY_LOCATION_INDEX, Persistable.OBJECT_BY_IMPACT_INDEX, Persistable.OBJECT_BY_IMPACT_ABS_INDEX})
        public String getStorageBucket() {
            if (StringUtils.isBlank(getId())) {
                return getClassStorageBucket(); // Fallback if ID isn't formed yet
            }
            // Assumes ID format is "LEG/ns/session/nativeId"
            String[] parts = getId().split("/");
            if (parts.length >= 4) {
                return String.join("/", parts[0], parts[1], parts[2], parts[3]); // Should be LEG/ns_part1/ns_part2/session
            }
            // Fallback to more generic bucket if ID format is unexpected, or use class-level bucket
            // The ID format is "LEG/us/congress/118/S000033"
            // So parts[0]=LEG, parts[1]=us, parts[2]=congress, parts[3]=118, parts[4]=S000033
            // We want "LEG/us/congress/118"
            if (getId().contains(LegislativeNamespace.US_CONGRESS.getNamespace())) {
                 return getId().substring(0, StringUtils.ordinalIndexOf(getId(), "/", 4));
            } else {
                 // For states like "us/california", namespace has two parts.
                 // ID could be "LEG/us/california/2023/L123"
                 // parts[0]=LEG, parts[1]=us, parts[2]=california, parts[3]=2023, parts[4]=L123
                 // We want "LEG/us/california/2023"
                 return getId().substring(0, StringUtils.ordinalIndexOf(getId(), "/", 4));
            }
        }
	@Override @JsonIgnore public void setStorageBucket(String prefix) { }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX }) public LocalDate getDate() { return birthday; }
	@JsonIgnore public void setDate(LocalDate date) { this.setBirthday(date); }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_RATING_INDEX }) public int getRating() { return interpretation == null ? -1 : interpretation.getRating(); }
	@JsonIgnore public void setRating(int rating) { }
	@JsonIgnore public int getRating(TrackedIssue issue) { return interpretation.getRating(issue); }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_RATING_ABS_INDEX }) public int getRatingAbs() { return interpretation == null ? -1 : Math.abs(interpretation.getRating()); }
	@JsonIgnore public void setRatingAbs(int rating) { }
	
	@DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_IMPACT_INDEX }) public Long getImpact() { return getImpact(TrackedIssue.OverallBenefitToSociety); }
	public void setImpact(Long impact) { impactMap.put(TrackedIssue.OverallBenefitToSociety, impact); }
	
	@DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_IMPACT_ABS_INDEX }) public Long getImpactAbs() { return Math.abs(getImpact()); }
	public void setImpactAbs(Long impact) { }
	
	// TODO : What could this be?
//	@DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_HOT_INDEX }) public int getHot() { return (int)(getImpactAbs() * Math.exp(-0.02 * ChronoUnit.DAYS.between(introducedDate, LocalDate.now()))); }
//	public void setHot(int hot) { }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_LOCATION_INDEX }) public String getLocation() { return this.terms.last().getState() + (this.terms.last().getDistrict() == null ? "" : "/" + this.terms.last().getDistrict() ); }
	@JsonIgnore public void setLocation(String location) { }
	
	public Long getImpact(TrackedIssue issue)
	{
		return impactMap.getOrDefault(issue, 0l);
	}
	
	@Data
	@DynamoDbBean
	@NoArgsConstructor
	@AllArgsConstructor
	public static class LegislatorName {
		
		protected String first;
		
		protected String last;
		
		protected String official_full;
		
	}
	
	@Data
	@DynamoDbBean
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class LegislativeTerm implements Comparable<LegislativeTerm> {
		
		protected LocalDate startDate;
		
		protected LocalDate endDate;
		
		protected String state;
		
		protected Integer district;
		
		protected Party party;
		
		protected LegislativeChamber chamber;

		@Override
		public int compareTo(LegislativeTerm o) {
			return this.startDate.compareTo(o.startDate);
		}
		
	}
	
	@DynamoDbBean(converterProviders = LegislatorBillInteractionSetConverterProvider.class)
	public static class LegislatorBillInteractionList extends ArrayList<LegislatorBillInteraction> {}
	
	@DynamoDbBean(converterProviders = LegislatorBillInteractionSetConverterProvider.class)
	public static class LegislatorBillInteractionSet extends TreeSet<LegislatorBillInteraction> {}
	
	@DynamoDbBean
	public static class LegislatorLegislativeTermSortedSet extends TreeSet<LegislativeTerm> {}

	@Override
	public int compareTo(Legislator o) {
		return Integer.valueOf(this.getRating()).compareTo(o.getRating());
	}
	
	// Getter and Setter for photoUrl
	public String getPhotoUrl() {
		return photoUrl;
	}

	public void setPhotoUrl(String photoUrl) {
		this.photoUrl = photoUrl;
	}
}
