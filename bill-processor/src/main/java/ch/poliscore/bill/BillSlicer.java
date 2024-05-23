package ch.poliscore.bill;

import java.util.List;

public interface BillSlicer {
	public static final int MAX_SECTION_LENGTH = 100000; //450000;
	
	public List<String> slice(String bill);
}
