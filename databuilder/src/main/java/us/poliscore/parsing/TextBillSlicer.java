package us.poliscore.parsing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.model.bill.BillText;

/**
 * Abandoned in favour of parsing the bill XML syntax. Challenges present in raw text parsing:
 * 
 * 1. Finding bill sections isn't something that lends itself easily to simple regex, given that "section" could be:
 *   - A listing in the bill table of contents
 *   - A reference to a different bill
 *   - A section header for the current billl
 */
public class TextBillSlicer implements BillSlicer {

	public boolean containsSectionHeader(String line)
	{
		final String lc = line.toLowerCase();
		
		return lc.matches(".*section \\d+.*") || lc.matches(".*sec\\. \\d+.*");
	}
	
	@Override
	public List<BillSlice> slice(Bill bill, BillText text, int maxSectionLength) {
		final List<BillSlice> sections = new ArrayList<BillSlice>();
		
//		try (final Scanner scanner = new Scanner(bill.getText()))
//		{
//			StringBuilder cur = new StringBuilder();
//			StringBuilder prev = null;
//			
//			while (scanner.hasNextLine()) {
//			  String line = scanner.nextLine();
//			  
//			  if (prev != null && prev.length() + cur.length() >= BillSlicer.MAX_SECTION_LENGTH)
//			  {
//				  sections.add(prev.toString());
//				  prev = null;
//			  }
//			  if (cur.length() >= BillSlicer.MAX_SECTION_LENGTH)
//			  {
//				  sections.add(cur.toString());
//				  cur = new StringBuilder();
//			  }
//			  
//			  if (cur.length() > 0)
//			  {
//				  if (containsSectionHeader(line))
//				  {
//					  if (prev != null)
//					  {
//						  prev = new StringBuilder(prev.toString() + cur.toString());
//						  cur = new StringBuilder();
//					  }
//					  else
//					  {
//						  prev = cur;
//						  cur = new StringBuilder();
//					  }
//				  }
//				  else if (cur.length() + line.length() >= BillSlicer.MAX_SECTION_LENGTH)
//				  {
//					  sections.add(cur.toString());
//					  cur = new StringBuilder();
//				  }
//			  }
//			  
//			  cur.append(line);
//			}
//			
//			sections.add(cur.toString());
//		}
		
		return sections;
	}

	public static List<String> slice(String text) {
		int aEnd = text.substring(0, (text.length() / 2) + 200).lastIndexOf("\\s");
		int bStart = text.substring((text.length() / 2) - 200).indexOf("\\s") + 1;
		
		return Arrays.asList(
			text.substring(0, aEnd),
			text.substring(bStart)
		);
	}
	
}
