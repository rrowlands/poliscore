package ch.poliscore.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public class BillText implements Persistable
{
	
	@NonNull
	protected String billId;
	
	@NonNull
	protected String xml;
	
	protected Date lastUpdated;
	
	@JsonIgnore
	public String getId()
	{
		return billId;
	}
	
}
