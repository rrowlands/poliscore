package ch.poliscore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ch.poliscore.bill.BillSlicer;
import ch.poliscore.bill.TextBillSlicer;

public class SlicerTest {
	@Test
	public void testTextSlicer() throws Exception
	{
		List<String> sliced = new TextBillSlicer().slice(getBillText());
		
		System.out.println("Sliced into " + sliced.size() + " sections.");
		
		for (int i = 0; i < sliced.size(); ++i)
		{
			if (sliced.get(i).length() >= BillSlicer.MAX_SECTION_LENGTH)
			{
				System.out.println(sliced.get(i));
				Assertions.fail("Section " + i + " was too long (" + sliced.get(i).length() + ")");
			}
		}
		
//		for (int i = 0; i < Math.min(sliced.size(), Integer.MAX_VALUE); ++i)
//		{
//			
//		}
		
		IOUtils.write(String.join("\n\n\n\n===========================\n\n\n\n", sliced), new FileOutputStream(new File("/Users/rrowlands/dev/projects/poliscore/bill-processor/target/test.txt")), "UTF-8");
	}
	
	private String getBillText()
	{
		try {
			return IOUtils.toString(SlicerTest.class.getResourceAsStream("/hr3935.xml"), "UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
