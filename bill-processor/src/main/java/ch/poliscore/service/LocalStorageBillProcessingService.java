package ch.poliscore.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import ch.poliscore.Environment;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LocalStorageBillProcessingService extends BillProcessingService {
	
	protected File getLocalStorage()
	{
		return Environment.getDeployedPath();
	}
	
	@Override
	protected String fetchBillText(String url)
    {
		try {
			File billStorage = new File(getLocalStorage(), "bills");
			File stored = new File(billStorage, billNameFromUrl(url) + ".txt");
			
			if (stored.exists())
			{
				return IOUtils.toString(new FileInputStream(stored), "UTF-8");
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return super.fetchBillText(url);
    }
	
	@Override
	protected String interpret(String text, String bill, int sliceIndex) {
		try {
			File interpretStorage = new File(getLocalStorage(), "interpretations");
			File stored = new File(interpretStorage, billNameFromUrl(bill) + ".txt");
			
			if (stored.exists())
			{
				return IOUtils.toString(new FileInputStream(stored), "UTF-8");
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return super.interpret(text, bill, sliceIndex);
	}
	
	@Override
	protected void archiveInterpretation(String interp, String bill, int sliceIndex)
	{
		try {
			File storage = new File(getLocalStorage(), "interpretations");
			File out = new File(storage, super.billNameFromUrl(bill) + ".txt");
			
			FileUtils.write(out, interp, "UTF-8");
			
			System.out.println("Wrote OpenAI response to " + out.getAbsolutePath());
		
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	protected void archiveBillText(String text, String bill) {
		super.archiveBillText(text, bill);
		
		try {
			File storage = new File(getLocalStorage(), "bills");
			File out = new File(storage, super.billNameFromUrl(bill) + ".txt");
			
			FileUtils.write(out, text, "UTF-8");
			
			System.out.println("Wrote bill text to " + out.getAbsolutePath());
		
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
