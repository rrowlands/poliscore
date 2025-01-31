package us.poliscore.model.bill;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Persistable;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill.BillSponsor;

@Data
@DynamoDbBean
@RegisterForReflection
@NoArgsConstructor
public class BillIssueImpact implements Persistable {
	public static final String ID_CLASS_PREFIX = "BII";
	
	public static String getIndexPrimaryKey(TrackedIssue issue)
	{
		return ID_CLASS_PREFIX + "/" + LegislativeNamespace.US_CONGRESS.getNamespace() + "/" + PoliscoreUtil.CURRENT_SESSION.getNumber() + "/" + issue.name();
	}
	
	public BillIssueImpact(TrackedIssue issue, int impact, Bill bill) {
		this.issue = issue;
		this.impact = impact;
		this.billId = bill.getId();
		this.name = bill.getName();
		this.sponsor = bill.getSponsor();
		this.shortExplain = bill.getInterpretation().getShortExplain();
		this.status = bill.getStatus();
		this.introducedDate = bill.getIntroducedDate();
		this.rating = bill.getRating();
	}
	
	protected TrackedIssue issue;
	
	@Getter(onMethod = @__({ @DynamoDbSecondarySortKey(indexNames = { Persistable.OBJECT_BY_ISSUE_IMPACT_INDEX }) }))
	protected int impact;
	
	protected String billId;
	
	protected String name;
	
	protected BillSponsor sponsor;
	
	protected String shortExplain;
	
	protected BillStatus status;
	
	protected LocalDate introducedDate;
	
	protected int rating;
	
	// BIL/us/congress/118/hr/8580
	@DynamoDbIgnore public BillType getType() { return BillType.valueOf(billId.split("/")[4].toUpperCase()); }
	
	@JsonIgnore @DynamoDbIgnore public String getSession() { return billId.split("/")[3]; }
	@JsonIgnore @DynamoDbIgnore public LegislativeNamespace getNamespace() { return LegislativeNamespace.of(billId.split("/")[1] + "/" + billId.split("/")[2]); }
	
	@DynamoDbSecondaryPartitionKey(indexNames = { Persistable.OBJECT_BY_ISSUE_IMPACT_INDEX })
	@JsonIgnore public String getIssuePK() { return ID_CLASS_PREFIX + "/" + getNamespace().getNamespace() + "/" + getSession() + "/" + issue.name(); }
	@JsonIgnore public void setIssuePK(String pk) { }
	
	@DynamoDbPartitionKey
	@JsonIgnore public String getId() { return getIssuePK() + "/" + billId; }
	@JsonIgnore public void setId(String id) { }
	
	@JsonIgnore public String getStorageBucket() { return null; }
	@JsonIgnore public void setStorageBucket(String prefix) { }
}
