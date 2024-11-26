package us.poliscore;

import java.io.InputStream;
import java.time.LocalDate;

import org.apache.commons.io.IOUtils;

import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillText;
import us.poliscore.model.bill.BillType;

public class BillGen {
	
	// Congress has a weird bill "Appropriations" format where most of the content is in the "tile" element and not the "section" elements.
	public static final InputStream btx118hr8550 = BillGen.class.getResourceAsStream("/btx/BILLS-118hr8580eh.xml");
	
	// Trump's EPA gutting bill
	public static final InputStream btx115hr806 = BillGen.class.getResourceAsStream("/btx/BILLS-115hr806rfs.xml");
	
	// Big transportation bill
	public static final InputStream btx118hr3935 = BillGen.class.getResourceAsStream("/btx/BILLS-118hr3935enr.xml");
	
	public static Bill gen() {
		val bill = new Bill();

		// TODO : Sponsor and cosponsor
		bill.setSession(118);
		bill.setType(BillType.HR);
		bill.setNumber(8580);
		bill.setName("The Best Bill Ever");
		bill.setIntroducedDate(LocalDate.now());
		
		return bill;
	}
	
	@SneakyThrows
	public static BillText genTxt(Bill bill, InputStream xml) {
		val text = new BillText();
		
		text.setBillId(bill.getId());
		text.setLastUpdated(LocalDate.now());
		text.setXml(IOUtils.toString(xml, "UTF-8"));
		
		return text;
	}
}
