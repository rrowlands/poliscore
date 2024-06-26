package us.poliscore.model.bill;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
public class BillSlice {
	@JsonIgnore
	private transient Bill bill;
	
	@JsonIgnore
	private transient String text;
	
	@JsonIgnore
	private transient int sliceIndex;
	
	private int sectionStart;
	
	private int sectionEnd;
}
