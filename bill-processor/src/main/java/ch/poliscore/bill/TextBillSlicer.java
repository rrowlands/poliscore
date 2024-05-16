package ch.poliscore.bill;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.io.IOUtils;

public class TextBillSlicer implements BillSlicer {

	public static void main(String[] args) throws MalformedURLException, IOException {
		String bill = IOUtils.toString(new URL("https://www.congress.gov/115/bills/hr806/BILLS-115hr806rfs.xml"), "UTF-8");
		List<String> sliced = new TextBillSlicer().slice(bill);
		
		System.out.println(sliced.get(0));
	}
	
	public boolean containsSectionHeader(String line)
	{
		final String lc = line.toLowerCase();
		
		return lc.matches(".*section \\d+.*") || lc.matches(".*sec\\. \\d+.*");
	}
	
	@Override
	public List<String> slice(String bill) {
		final List<String> sections = new ArrayList<String>();
		
		try (final Scanner scanner = new Scanner(bill))
		{
			StringBuilder cur = new StringBuilder();
			StringBuilder prev = null;
			
			while (scanner.hasNextLine()) {
			  String line = scanner.nextLine();
			  
			  if (containsSectionHeader(line))
			  {
				  if (prev != null && prev.length() < BillSlicer.MAX_SECTION_LENGTH)
				  {
					  
				  }
				  
				  sections.add(cur.toString());
				  
				  prev = cur;
				  cur = new StringBuilder();
			  }
			  
			  cur.append(line);
			}
			
			sections.add(cur.toString());
		}
		
		return sections;
	}
	
}
