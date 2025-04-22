package us.poliscore.parsing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
//			  if (prev != null && prev.length() + cur.length() >= OpenAIService.MAX_SECTION_LENGTH)
//			  {
//				  sections.add(prev.toString());
//				  prev = null;
//			  }
//			  if (cur.length() >= OpenAIService.MAX_SECTION_LENGTH)
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
//				  else if (cur.length() + line.length() >= OpenAIService.MAX_SECTION_LENGTH)
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
		int aEnd = lastIndexOfRegex(text.substring(0, (text.length() / 2) + 200), "\\s");
		int bStart = ((text.length() / 2) - 200) + indexOfRegex(text.substring((text.length() / 2) - 200), "\\s") + 1;
		
		if (aEnd == -1 || bStart == -1) {
			return Arrays.asList(
				text.substring(0, text.length()/2),
				text.substring(text.length()/2)
			);
		}
				
		return Arrays.asList(
			text.substring(0, aEnd),
			text.substring(bStart)
		);
	}
	
	public static int indexOfRegex(String str, String regex)
	{
		Pattern p = Pattern.compile(regex);  // insert your pattern here
		Matcher m = p.matcher(str);
		if (m.find()) {
		   return m.start();
		}
		return -1;
	}
	
	/**
	 * Version of lastIndexOf that uses regular expressions for searching.
	 * 
	 * @param str String in which to search for the pattern.
	 * @param toFind Pattern to locate.
	 * @return The index of the requested pattern, if found; NOT_FOUND (-1) otherwise.
	 */
	public static int lastIndexOfRegex(String str, String toFind)
	{
	    Pattern pattern = Pattern.compile(toFind);
	    Matcher matcher = pattern.matcher(str);
	    
	    // Default to the NOT_FOUND constant
	    int lastIndex = -1;
	    
	    // Search for the given pattern
	    while (matcher.find())
	    {
	        lastIndex = matcher.start();
	    }
	    
	    return lastIndex;
	}
}
