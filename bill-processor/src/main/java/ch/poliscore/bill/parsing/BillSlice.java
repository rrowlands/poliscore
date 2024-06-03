package ch.poliscore.bill.parsing;

import ch.poliscore.bill.Bill;
import lombok.Data;

@Data
public class BillSlice {
	private transient Bill bill;
	
	private transient String text;
	
	private int sectionStart;
	
	private int sectionEnd;
}
