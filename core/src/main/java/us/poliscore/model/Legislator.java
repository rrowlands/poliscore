package us.poliscore.model;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import us.poliscore.model.dynamodb.JacksonAttributeConverter.CompressedLegislatorBillInteractionSetConverter;
import us.poliscore.model.dynamodb.JacksonAttributeConverter.LegislatorLegislativeTermSortedSetConverter;

@Data
@DynamoDbBean
@NoArgsConstructor
@RegisterForReflection
public class Legislator implements Persistable {
	
	public static final String ID_CLASS_PREFIX = "LEG";
	
	@NonNull
	protected LegislatorName name;
	
	protected String bioguideId;
	
	protected String thomasId;
	
	protected String wikidataId;
	
	protected LegislatorInterpretation interpretation;
	
//	@NonNull
	protected LocalDate birthday;
	
	@NonNull
	@Getter(onMethod = @__({ @DynamoDbConvertedBy(LegislatorLegislativeTermSortedSetConverter.class)}))
	protected LegislatorLegislativeTermSortedSet terms;
	
	@NonNull
	@Getter(onMethod = @__({ @DynamoDbConvertedBy(CompressedLegislatorBillInteractionSetConverter.class)}))
	protected LegislatorBillInteractionSet interactions = new LegislatorBillInteractionSet();
	
	@DynamoDbPartitionKey
	public String getId()
	{
		if (bioguideId != null) return generateId(LegislativeNamespace.US_CONGRESS, bioguideId);
		if (thomasId != null) return generateId(LegislativeNamespace.US_CONGRESS, thomasId);
		
		throw new NullPointerException();
	}
	
	public void setId(String id) { this.bioguideId = id; }
	
	public void addBillInteraction(LegislatorBillInteraction incoming)
	{
		interactions.removeIf(existing -> incoming.supercedes(existing));
		interactions.add(incoming);
	}
	
	public static String generateId(LegislativeNamespace ns, String bioguideId)
	{
		return ID_CLASS_PREFIX + "/" + ns.getNamespace() + "/" + bioguideId;
	}
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX }) public String getIdClassPrefix() { return ID_CLASS_PREFIX; }
	
	@Override @JsonIgnore public void setIdClassPrefix(String prefix) { }
	
	@Override @JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX }) public LocalDate getDate() { return terms.last().startDate; }

	@Override @JsonIgnore public void setDate(LocalDate date) { }
	
	@Override @JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_RATING_INDEX }) public int getRating() { return interpretation.getRating(); }

	@Override @JsonIgnore public void setRating(int rating) { }
	
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
		
		protected String party;
		
		protected CongressionalChamber chamber;

		@Override
		public int compareTo(LegislativeTerm o) {
			return this.startDate.compareTo(o.startDate);
		}
		
	}
	
	public static enum CongressionalChamber {
		
		SENATE,
		HOUSE
		
	}
	
	@DynamoDbBean
	public static class LegislatorBillInteractionSet extends HashSet<LegislatorBillInteraction> {}
	
	@DynamoDbBean
	public static class LegislatorLegislativeTermSortedSet extends TreeSet<LegislativeTerm> {}
	
}
