package us.poliscore.model.session;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import us.poliscore.model.AIInterpretationMetadata;
import us.poliscore.model.IssueStats;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Party;
import us.poliscore.model.Persistable;
import us.poliscore.model.bill.Bill.BillSponsor;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.dynamodb.DdbDataPage;
import us.poliscore.model.dynamodb.JacksonAttributeConverter.LegislatorBillInteractionSetConverterProvider;
import us.poliscore.model.dynamodb.JacksonAttributeConverter.AIInterpretationMetadataConverter;
import us.poliscore.model.dynamodb.JacksonAttributeConverter.CompressedPartyStatsConverter;
import us.poliscore.model.legislator.Legislator;

@Data
@DynamoDbBean
@RegisterForReflection
@NoArgsConstructor
public class SessionInterpretation implements Persistable {
	
	public static final String ID_CLASS_PREFIX = "SIT";
	
	protected int session;
	
//	@Getter(onMethod = @__({ @DynamoDbConvertedBy(PartyStatsMapAttributeConverter.class) }))
//	protected Map<Party, PartyStats> partyStats = new HashMap<Party, PartyStats>();
	
	@Getter(onMethod = @__({ @DdbDataPage("1"), @DynamoDbConvertedBy(CompressedPartyStatsConverter.class) }))
	protected PartyInterpretation democrat;
	
	@Getter(onMethod = @__({ @DdbDataPage("2"), @DynamoDbConvertedBy(CompressedPartyStatsConverter.class) }))
	protected PartyInterpretation republican;
	
	@Getter(onMethod = @__({ @DdbDataPage("3"), @DynamoDbConvertedBy(CompressedPartyStatsConverter.class) }))
	protected PartyInterpretation independent;
	
	@NonNull
	@Getter(onMethod = @__({ @DynamoDbConvertedBy(AIInterpretationMetadataConverter.class)}))
	protected AIInterpretationMetadata metadata;
	
	public static String generateId(int congress)
	{
		return ID_CLASS_PREFIX + "/" + LegislativeNamespace.US_CONGRESS.getNamespace() + "/" + congress;
	}
	
	@DynamoDbPartitionKey
	public String getId()
	{
		return generateId(session);
	}
	
	public void setId(String id) { }

	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX }) public String getIdClassPrefix() { return ID_CLASS_PREFIX; }
	@Override @JsonIgnore public void setIdClassPrefix(String prefix) { }

	@Data
	@DynamoDbBean
	@NoArgsConstructor
	@RegisterForReflection
	@AllArgsConstructor
	public static class PartyInterpretation {
		protected Party party;
		
		protected IssueStats stats;
		
		protected String longExplain;
		
		@NonNull
		protected PartyBillSet bestBills = new PartyBillSet();
		
		@NonNull
		protected PartyBillSet worstBills = new PartyBillSet();
		
		@NonNull
		protected PartyLegislatorSet bestLegislators = new PartyLegislatorSet();
		
		@NonNull
		protected PartyLegislatorSet worstLegislators = new PartyLegislatorSet();
	}
	
	@Data
	@DynamoDbBean
	@NoArgsConstructor
	@AllArgsConstructor
	@RegisterForReflection
	@EqualsAndHashCode
	public static class PartyBillInteraction implements Comparable<PartyBillInteraction>
	{
		@NonNull
		protected String id;
		
		@NonNull
		@EqualsAndHashCode.Exclude
		protected String name;
		
		@NonNull
		@EqualsAndHashCode.Exclude
		protected BillType type;
		
		@EqualsAndHashCode.Exclude
		protected LocalDate introducedDate;
		
		@EqualsAndHashCode.Exclude
		protected BillSponsor sponsor;
		
		@EqualsAndHashCode.Exclude
		protected List<BillSponsor> cosponsors;
		
		@EqualsAndHashCode.Exclude
		protected Integer rating;
		
		@EqualsAndHashCode.Exclude
		protected String shortExplain;
		
		@DynamoDbIgnore
		@JsonIgnore
		public String getShortExplainForInterp() {
			return this.name + " (" + (rating > 0 ? "+" : "") + rating + ", " + (1 + cosponsors.size()) + " sponsors" + ")" + ": " + this.shortExplain;
		}
		
		@DynamoDbIgnore
		@JsonIgnore
		public Double getWeight() {
			return (double)rating * (cosponsors.size() + 1);
		}

		@Override
		public int compareTo(PartyBillInteraction o) {
			return getWeight().compareTo(o.getWeight());
		}
	}
	
	/**
	 * TreeSet was decided against for these methods because TreeSet doesn't allow duplicates on the 'compareTo' method (which is sorted on rating for legislators)
	 */
	
	@DynamoDbBean(converterProviders = LegislatorBillInteractionSetConverterProvider.class)
	@NoArgsConstructor
	public static class PartyBillSet extends ArrayList<PartyBillInteraction> {

		public PartyBillSet(Collection c) {
			super(c);
		}
	}
	
	@DynamoDbBean(converterProviders = LegislatorBillInteractionSetConverterProvider.class)
	@NoArgsConstructor
	public static class PartyLegislatorSet extends ArrayList<Legislator> {

		public PartyLegislatorSet(Collection c) {
			super(c);
		}
	}
}