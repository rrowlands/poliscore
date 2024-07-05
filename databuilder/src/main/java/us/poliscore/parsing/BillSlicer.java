package us.poliscore.parsing;

import java.util.List;

import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.model.bill.BillText;

public interface BillSlicer {
	public static final int MAX_SECTION_LENGTH = 80000; //450000;
	
	public List<BillSlice> slice(Bill bill, BillText btx, int maxSectionLength);
}
