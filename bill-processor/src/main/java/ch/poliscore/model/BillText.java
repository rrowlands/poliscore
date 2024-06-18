package ch.poliscore.model;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BillText implements Persistable
{
	
	@NonNull
	protected String billId;
	
	@NonNull
	protected String xml;
	
	protected LocalDate lastUpdated;
	
	@JsonIgnore
	@DynamoDbPartitionKey
	public String getId()
	{
		return billId;
	}
	
}
