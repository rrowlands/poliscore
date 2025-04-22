package us.poliscore.parsing;

import java.util.List;

import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.model.bill.BillText;

public interface BillSlicer {
	
	// Was set to 80000 for GPT-4o for some reason... GPT-4o supports 128K tokens, which is about 400K characters.
//	public static final int MAX_SECTION_LENGTH = 80000; //400000;
	
	// This one is for GPT-4.1. The context window is slightly more than 1 million tokens, which multiplied by 4 is about 4 million characters. 
//	public static int MAX_SECTION_LENGTH = 3500000;
	
	public List<BillSlice> slice(Bill bill, BillText btx, int maxSectionLength);
}
