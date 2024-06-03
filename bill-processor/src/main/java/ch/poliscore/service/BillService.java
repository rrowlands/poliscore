package ch.poliscore.service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.UUID;

import org.apache.commons.io.IOUtils;

import ch.poliscore.bill.Bill;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BillService {
	public Bill fetchBill(String url)
    {
    	String text = fetchBillText(url);
    	
    	Bill bill = new Bill();
    	bill.setText(text);
    	bill.setName(generateBillName(url));
    	bill.setUrl(url);
    	bill.setId(UUID.randomUUID().toString());
    	
    	archiveBill(bill);
    	
    	return bill;
    }
    
    protected String generateBillName(String url)
    {
    	return generateBillName(url, -1);
    }
    
    protected String generateBillName(String url, int sliceIndex)
    {
    	try {
			URI uri = new URI(url);
			String path = uri.getPath();
			String billName = path.substring(path.lastIndexOf('/') + 1);
			
			if (billName.contains("."))
			{
				billName = billName.substring(0, billName.lastIndexOf("."));
			}
			
			if (billName.contains("BILLS-"))
			{
				billName = billName.replace("BILLS-", "");
			}
			
			if (sliceIndex != -1)
			{
				billName += "-" + String.valueOf(sliceIndex);
			}

			return billName;
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
    }
    
    protected String fetchBillText(String url)
    {
    	String billText;
    	try {
			billText = IOUtils.toString(new URL(url), "UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
    	
    	System.out.println("Fetched bill text from url [" + url + "].");
    	
    	return billText;
    }
    
    protected void archiveBill(Bill bill)
    {
    	// TODO : Dynamodb
    }
}
