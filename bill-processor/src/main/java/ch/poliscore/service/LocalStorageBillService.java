package ch.poliscore.service;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.poliscore.Environment;
import ch.poliscore.bill.Bill;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LocalStorageBillService extends BillService {
	
	protected File getLocalStorage()
	{
		return Environment.getDeployedPath();
	}
	
	@Override
	public Bill fetchBill(String url) {
		try {
			File billStorage = new File(getLocalStorage(), "bills");
			File stored = new File(billStorage, generateBillName(url) + ".json");
			
			if (stored.exists())
			{
				var mapper = new ObjectMapper();
				return mapper.readValue(stored, Bill.class);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return super.fetchBill(url);
	}
	
	@Override
	protected void archiveBill(Bill bill) {
		super.archiveBill(bill);
		
		try {
			File storage = new File(getLocalStorage(), "bills");
			File out = new File(storage, bill.getName() + ".json");
			
			var mapper = new ObjectMapper();
			mapper.writerWithDefaultPrettyPrinter().writeValue(out, bill);
			
			System.out.println("Wrote bill text to " + out.getAbsolutePath());
		
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
