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
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import us.poliscore.model.AIInterpretationMetadata;
import us.poliscore.model.IssueStats;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Party;
import us.poliscore.model.Persistable;
import us.poliscore.model.bill.Bill.BillSponsorOld;
import us.poliscore.model.bill.BillStatus;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.dynamodb.DdbDataPage;
import us.poliscore.model.dynamodb.JacksonAttributeConverter.AIInterpretationMetadataConverter;
import us.poliscore.model.dynamodb.JacksonAttributeConverter.LegislatorBillInteractionSetConverterProvider;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.dynamodb.JacksonAttributeConverter.*;

@Data
@DynamoDbBean
@RegisterForReflection
@NoArgsConstructor
public class SessionInterpretationOld implements Persistable {
	
	public static final String ID_CLASS_PREFIX = "SIT";
	
	protected int session;
	
//	@Getter(onMethod = @__({ @DynamoDbConvertedBy(PartyStatsMapAttributeConverter.class) }))
//	protected Map<Party, PartyStats> partyStats = new HashMap<Party, PartyStats>();
	
	@Getter(onMethod = @__({ @DdbDataPage("1"), @DynamoDbConvertedBy(CompressedPartyStatsConverter.class) }))
	protected PartyInterpretationOld democrat;
	
	@Getter(onMethod = @__({ @DdbDataPage("2"), @DynamoDbConvertedBy(CompressedPartyStatsConverter.class) }))
	protected PartyInterpretationOld republican;
	
	@Getter(onMethod = @__({ @DdbDataPage("3"), @DynamoDbConvertedBy(CompressedPartyStatsConverter.class) }))
	protected PartyInterpretationOld independent;
	
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

	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX }) public String getStorageBucket() { return ID_CLASS_PREFIX; }
	@Override @JsonIgnore public void setStorageBucket(String prefix) { }

	@Data
	@DynamoDbBean
	@NoArgsConstructor
	@RegisterForReflection
	@AllArgsConstructor
	public static class PartyInterpretationOld {
		protected Party party;
		
		protected IssueStats stats;
		
		@NonNull
		protected String longExplain;
		
		@NonNull
		protected PartyBillSetOld mostImportantBills = new PartyBillSetOld();
		
		@NonNull
		protected PartyBillSetOld leastImportantBills = new PartyBillSetOld();
		
		@NonNull
		protected PartyBillSetOld bestBills = new PartyBillSetOld();
		
		@NonNull
		protected PartyBillSetOld worstBills = new PartyBillSetOld();
		
		@NonNull
		protected PartyLegislatorSetOld bestLegislators = new PartyLegislatorSetOld();
		
		@NonNull
		protected PartyLegislatorSetOld worstLegislators = new PartyLegislatorSetOld();
	}
	
	@Data
	@DynamoDbBean
	@NoArgsConstructor
	@AllArgsConstructor
	@RegisterForReflection
	@EqualsAndHashCode
	public static class PartyBillInteractionOld implements Comparable<PartyBillInteractionOld>
	{
		@NonNull
		protected String id;
		
		@NonNull
		@EqualsAndHashCode.Exclude
		protected String name;
		
		@NonNull
		@EqualsAndHashCode.Exclude
		protected BillStatus billStatus;
		
		@NonNull
		@EqualsAndHashCode.Exclude
		protected BillType type;
		
		@EqualsAndHashCode.Exclude
		protected LocalDate introducedDate;
		
		@EqualsAndHashCode.Exclude
		protected BillSponsorOld sponsor;
		
		@EqualsAndHashCode.Exclude
		protected List<BillSponsorOld> cosponsors;
		
		@EqualsAndHashCode.Exclude
		protected Integer rating;
		
		@EqualsAndHashCode.Exclude
		protected Integer impact;
		
		@EqualsAndHashCode.Exclude
		protected String shortExplain;
		
		@DynamoDbIgnore
		@JsonIgnore
		public String getShortExplainForInterp() {
			return "- " + this.name + " (" + billStatus.getDescription() + ") (" + (cosponsors.size()) + " cosponsors" + ")" + ": " + this.shortExplain;
		}
		
		@DynamoDbIgnore
		@JsonIgnore
		public Integer getImpact() {
			return impact;
		}

		@Override
		public int compareTo(PartyBillInteractionOld o) {
			return getImpact().compareTo(o.getImpact());
		}
	}
	
	/**
	 * TreeSet was decided against for these methods because TreeSet doesn't allow duplicates on the 'compareTo' method (which is sorted on rating for legislators)
	 */
	
	@DynamoDbBean(converterProviders = LegislatorBillInteractionSetConverterProvider.class)
	@NoArgsConstructor
	public static class PartyBillSetOld extends ArrayList<PartyBillInteractionOld> {

		public PartyBillSetOld(Collection c) {
			super(c);
		}
	}
	
	@DynamoDbBean(converterProviders = LegislatorBillInteractionSetConverterProvider.class)
	@NoArgsConstructor
	public static class PartyLegislatorSetOld extends ArrayList<Legislator> {

		public PartyLegislatorSetOld(Collection c) {
			super(c);
		}
	}
}
