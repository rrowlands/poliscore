package ch.poliscore.bill.parsing;

import java.util.List;

import ch.poliscore.model.Bill;

public interface BillSlicer {
	public static final int MAX_SECTION_LENGTH = 80000; //450000;
	
	public List<BillSlice> slice(Bill bill);
}
