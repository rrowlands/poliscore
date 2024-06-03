package ch.poliscore;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ch.poliscore.bill.Bill;
import ch.poliscore.bill.parsing.BillSlice;
import ch.poliscore.bill.parsing.BillSlicer;
import ch.poliscore.bill.parsing.XMLBillSlicer;
import ch.poliscore.service.TestResourcesBillService;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.common.constraint.Assert;
import jakarta.inject.Inject;

@QuarkusTest
public class SlicerTest {
	
	@Inject
	private TestResourcesBillService billService;
	
	@Test
	public void testTextSlicer() throws Exception
	{
		Bill bill = billService.fetchBill(TestUtils.C118HR393);
		List<BillSlice> sliced = new XMLBillSlicer().slice(bill);
		
		System.out.println("Sliced into " + sliced.size() + " sections.");
		
		for (int i = 0; i < sliced.size(); ++i)
		{
			Assert.assertTrue(sliced.get(i).getText().length() > 0);
			
			if (sliced.get(i).getText().length() >= BillSlicer.MAX_SECTION_LENGTH)
			{
				System.out.println(sliced.get(i));
				Assertions.fail("Slice " + i + " was too long (" + sliced.get(i).getText().length() + ")");
			}
		}
		
		IOUtils.write(String.join("\n\n\n\n===========================\n\n\n\n", sliced.stream().map(s -> s.getText()).collect(Collectors.toList())),
				new FileOutputStream(new File("/Users/rrowlands/dev/projects/poliscore/bill-processor/target/slice-test.txt")), "UTF-8");
	}
}
