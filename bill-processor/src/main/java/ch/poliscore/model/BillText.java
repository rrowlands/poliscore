package ch.poliscore.model;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BillText implements Persistable
{
	public static final String ID_CLASS_PREFIX = "BTX";
	
	@NonNull
	protected String billId;
	
	@NonNull
	protected String xml;
	
	protected LocalDate lastUpdated;
	
	@JsonIgnore
	public String getId()
	{
		return generateId(billId);
	}
	
	public void setId(String id) { this.billId = id; }
	
	public static String generateId(String billId) { return billId.replace(Bill.ID_CLASS_PREFIX, ID_CLASS_PREFIX); }
	
	@Override @JsonIgnore @DynamoDbIgnore public String getIdClassPrefix() { return ID_CLASS_PREFIX; }
	
}
