package us.poliscore.model.legislator;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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
	public static String getClassStorageBucket()
	{
		return ID_CLASS_PREFIX + "/" + LegislativeNamespace.US_CONGRESS.getNamespace() + "/" + PoliscoreUtil.CURRENT_SESSION.getNumber();
	}
	
	@NonNull
	protected LegislatorName name;
	
	protected Integer session;
	
	protected String bioguideId;
	
	protected String thomasId;
	
	// Senate Id : https://github.com/usgpo/bill-status/issues/241
	protected String lisId;
	
	protected String wikidataId;
	
	protected LegislatorInterpretation interpretation;
	
	@NonNull
	protected LocalDate birthday;
	
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
	public String getId()
	{
		if (bioguideId != null) return generateId(LegislativeNamespace.US_CONGRESS, session, bioguideId);
		if (thomasId != null) return generateId(LegislativeNamespace.US_CONGRESS, session, thomasId);
		
		throw new NullPointerException();
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
	
	public static String generateId(LegislativeNamespace ns, Integer session, String bioguideId)
	{
		return generateId(ns, session.toString(), bioguideId);
	}
	
	public static String generateId(LegislativeNamespace ns, String session, String bioguideId)
	{
		return ID_CLASS_PREFIX + "/" + ns.getNamespace() + "/" + session + "/" + bioguideId;
	}
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX, Persistable.OBJECT_BY_RATING_ABS_INDEX, Persistable.OBJECT_BY_LOCATION_INDEX, Persistable.OBJECT_BY_IMPACT_INDEX, Persistable.OBJECT_BY_IMPACT_ABS_INDEX}) public String getStorageBucket() {
		if (!StringUtils.isEmpty(this.getId()))
			return this.getId().substring(0, StringUtils.ordinalIndexOf(getId(), "/", 4));
		
		return getClassStorageBucket();
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
	
}
