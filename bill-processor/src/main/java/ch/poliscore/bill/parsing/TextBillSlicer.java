package ch.poliscore.bill.parsing;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.io.IOUtils;

import ch.poliscore.model.Bill;

/**
 * Abandoned in favour of parsing the bill XML syntax. Challenges present in raw text parsing:
 * 
 * 1. Finding bill sections isn't sometihng that lends itself easily to simple regex, given that "section" could be:
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
	public List<BillSlice> slice(Bill bill) {
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
	
}
