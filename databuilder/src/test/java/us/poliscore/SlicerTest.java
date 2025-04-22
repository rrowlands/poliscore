package us.poliscore;

import static org.joox.JOOX.$;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import lombok.val;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.parsing.XMLBillSlicer;
import us.poliscore.service.OpenAIService;

@QuarkusTest
public class SlicerTest {
	
	
	/**
	 * Congress has a weird bill format where most of the content is in the "tile" element and not the "section" elements.
	 */
	@Test
	public void testXmlSliceAppropriationsBill() throws Exception
	{
		OpenAIService.MAX_SECTION_LENGTH = 80000;
		
		Bill bill = BillGen.gen();
		bill.setText(BillGen.genTxt(bill, BillGen.btx118hr8550));
		
		List<BillSlice> sliced = new XMLBillSlicer().slice(bill, bill.getText(), OpenAIService.MAX_SECTION_LENGTH);
		
		Assertions.assertTrue(sliced.size() > 1);
		Assertions.assertTrue(sliced.size() < 6);
		
		System.out.println("Sliced into " + sliced.size() + " sections.");
		
		for (int i = 0; i < sliced.size(); ++i)
		{
//			Assert.assertTrue(sliced.get(i).getText().length() > 0);
			
			if (sliced.get(i).getText().length() >= OpenAIService.MAX_SECTION_LENGTH)
			{
				System.out.println(sliced.get(i));
				Assertions.fail("Slice " + i + " was too long (" + sliced.get(i).getText().length() + ")");
			}
			
			validateXpaths(sliced.get(i));
		}
		
		val f = new File(Environment.getDeployedPath(), "slice-test.txt");
		System.out.println("Writing to " + f.getAbsolutePath());
		
		IOUtils.write(String.join("\n\n\n\n===========================\n\n\n\n", sliced.stream().map(s -> s.getText()).collect(Collectors.toList())),
				new FileOutputStream(f), "UTF-8");
	}
	
	private void validateXpaths(BillSlice billSlice) {
		Assertions.assertTrue(StringUtils.isNotBlank(billSlice.getStart()));
		Assertions.assertTrue($(XMLBillSlicer.toDoc(billSlice.getBill().getText().getXml())).xpath(billSlice.getStart()).isNotEmpty());
		Assertions.assertTrue(StringUtils.isNotBlank(billSlice.getEnd()));
		Assertions.assertTrue($(XMLBillSlicer.toDoc(billSlice.getBill().getText().getXml())).xpath(billSlice.getEnd()).isNotEmpty());
	}

	/**
	 * This appears to be the most common bill format.
	 */
	@Test
	public void testXmlSliceLargeBill() throws Exception
	{
		OpenAIService.MAX_SECTION_LENGTH = 80000;
		
		Bill bill = BillGen.gen();
		bill.setText(BillGen.genTxt(bill, BillGen.btx118hr3935));
		
		List<BillSlice> sliced = new XMLBillSlicer().slice(bill, bill.getText(), OpenAIService.MAX_SECTION_LENGTH);
		
		Assertions.assertTrue(sliced.size() > 1);
		
		System.out.println("Sliced into " + sliced.size() + " sections.");
		
		for (int i = 0; i < sliced.size(); ++i)
		{
//			Assert.assertTrue(sliced.get(i).getText().length() > 0);
			
			if (sliced.get(i).getText().length() >= OpenAIService.MAX_SECTION_LENGTH)
			{
				System.out.println(sliced.get(i));
				Assertions.fail("Slice " + i + " was too long (" + sliced.get(i).getText().length() + ")");
			}
			
			validateXpaths(sliced.get(i));
		}
		
		val f = new File(Environment.getDeployedPath(), "slice-test.txt");
		System.out.println("Writing to " + f.getAbsolutePath());
		
		IOUtils.write(String.join("\n\n\n\n===========================\n\n\n\n", sliced.stream().map(s -> s.getText()).collect(Collectors.toList())),
				new FileOutputStream(f), "UTF-8");
	}
	
	/**
	 * Slice a tiny bill that doesn't even need to be sliced
	 */
	@Test
	public void testXmlSliceSmallBill() throws Exception
	{
		OpenAIService.MAX_SECTION_LENGTH = 80000;
		
		Bill bill = BillGen.gen();
		bill.setText(BillGen.genTxt(bill, BillGen.btx115hr806));
		
		List<BillSlice> sliced = new XMLBillSlicer().slice(bill, bill.getText(), OpenAIService.MAX_SECTION_LENGTH);
		
		Assertions.assertTrue(sliced.size() == 1);
		
		System.out.println("Sliced into " + sliced.size() + " sections.");
		
		for (int i = 0; i < sliced.size(); ++i)
		{
//			Assert.assertTrue(sliced.get(i).getText().length() > 0);
			
			if (sliced.get(i).getText().length() >= OpenAIService.MAX_SECTION_LENGTH)
			{
				System.out.println(sliced.get(i));
				Assertions.fail("Slice " + i + " was too long (" + sliced.get(i).getText().length() + ")");
			}
		}
		
		val f = new File(Environment.getDeployedPath(), "slice-test.txt");
		System.out.println("Writing to " + f.getAbsolutePath());
		
		IOUtils.write(String.join("\n\n\n\n===========================\n\n\n\n", sliced.stream().map(s -> s.getText()).collect(Collectors.toList())),
				new FileOutputStream(f), "UTF-8");
	}
}
