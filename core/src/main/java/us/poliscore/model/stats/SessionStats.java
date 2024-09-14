package us.poliscore.model.stats;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import us.poliscore.model.IssueStats;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Party;
import us.poliscore.model.Persistable;
import us.poliscore.model.bill.Bill.BillSponsor;
import us.poliscore.model.dynamodb.JacksonAttributeConverter.LegislatorBillInteractionSetConverterProvider;
import us.poliscore.model.dynamodb.PartyStatsMapAttributeConverter;
import us.poliscore.model.legislator.Legislator;

@Data
@DynamoDbBean
@RegisterForReflection
public class SessionStats implements Persistable {
	
	public static final String ID_CLASS_PREFIX = "SST";
	
	protected int session;
	
	@Getter(onMethod = @__({ @DynamoDbConvertedBy(PartyStatsMapAttributeConverter.class) }))
	protected Map<Party, PartyStats> partyStats = new HashMap<Party, PartyStats>();
	
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
	public static class PartyStats {
		protected Party party;
		
		protected IssueStats stats;
		
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
		protected String billId;
		
		@NonNull
		@EqualsAndHashCode.Exclude
		protected String billName;
		
		@EqualsAndHashCode.Exclude
		protected BillSponsor sponsor;
		
		@EqualsAndHashCode.Exclude
		protected List<BillSponsor> cosponsors;
		
		@EqualsAndHashCode.Exclude
		protected Integer benefitToSociety;
		
		@DynamoDbIgnore
		public Double getWeight() {
			return (double)benefitToSociety * (cosponsors.size() + 1);
		}

		@Override
		public int compareTo(PartyBillInteraction o) {
			return getWeight().compareTo(o.getWeight());
		}
	}
	
	@DynamoDbBean(converterProviders = LegislatorBillInteractionSetConverterProvider.class)
	@NoArgsConstructor
	public static class PartyBillSet extends TreeSet<PartyBillInteraction> {

		public PartyBillSet(Collection c) {
			super(c);
		}
	}
	
	@DynamoDbBean(converterProviders = LegislatorBillInteractionSetConverterProvider.class)
	@NoArgsConstructor
	public static class PartyLegislatorSet extends TreeSet<Legislator> {

		public PartyLegislatorSet(Collection c) {
			super(c);
		}
	}
}
