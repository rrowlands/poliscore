package ch.poliscore;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import ch.poliscore.bill.TextBillSlicer;

public class SlicerTest {
	@Test
	public void testTextSlicer()
	{
		List<String> sliced = new TextBillSlicer().slice(getBillText());
		
		System.out.println("Sliced into " + sliced.size() + " sections.");
		
		for (int i = 0; i < Math.min(sliced.size(), 4); ++i)
		{
			System.out.println(sliced.get(i));
		}
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
