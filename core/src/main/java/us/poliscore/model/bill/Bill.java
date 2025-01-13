package us.poliscore.model.bill;

import java.time.LocalDate;
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
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.LegislativeChamber;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Party;
import us.poliscore.model.Persistable;

@Data
@DynamoDbBean
@RegisterForReflection
public class Bill implements Persistable {
	
	public static final String ID_CLASS_PREFIX = "BIL";
	
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
	
	@Override @JsonIgnore @DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX, Persistable.OBJECT_BY_RATING_INDEX, Persistable.OBJECT_BY_IMPORTANCE_INDEX }) public String getIdClassPrefix() { return ID_CLASS_PREFIX; }
	@Override @JsonIgnore public void setIdClassPrefix(String prefix) { }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_DATE_INDEX }) public LocalDate getDate() { return introducedDate; }
	@JsonIgnore public void setDate(LocalDate date) { introducedDate = date; }
	
	@JsonIgnore @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_RATING_INDEX }) public int getRating() { return interpretation.getRating(); }
	@JsonIgnore public void setRating(int rating) { }
	
	/*
	 * A percentage of how much of the chamber has cosponsored the bill. In the house this number is 435. In the senate this is 100.
	 */
	private float getCosponsorPercent()
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
	
	@DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_IMPORTANCE_INDEX }) public int getImportance() { return Math.abs((int)( (float)interpretation.getRating() + ((status.getProgress())*150f) + (getCosponsorPercent()*50) )); }
	public void setImportance(int rating) { }
	
	public static String generateId(int congress, BillType type, int number)
	{
		return ID_CLASS_PREFIX + "/" + LegislativeNamespace.US_CONGRESS.getNamespace() + "/" + congress + "/" + type.getName().toLowerCase() + "/" + number;
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
