package ch.poliscore.service;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.poliscore.Environment;
import ch.poliscore.bill.Bill;
import ch.poliscore.bill.BillInterpretation;
import jakarta.inject.Inject;

public class LocalStorageBillInterpretationService extends BillInterpretationService {
	
	@Inject
	protected BillService billService;
	
	protected File getLocalStorage()
	{
		return Environment.getDeployedPath();
	}
	
	protected boolean allowFetch()
	{
		return true;
	}
	
	@Override
	public BillInterpretation interpret(Bill bill) {
		try {
			File interpretStorage = new File(getLocalStorage(), "interpretations");
			File stored = new File(interpretStorage, bill.getName() + ".json");
			
			if (stored.exists())
			{
				var mapper = new ObjectMapper();
				BillInterpretation interp = mapper.readValue(stored, BillInterpretation.class);
				interp.setBill(billService.fetchBill(interp.getBillUrl()));
				return interp;
			}
			
			if (!allowFetch()) throw new RuntimeException("Could not find interpretation in local storage at " + stored.getAbsolutePath() + " and allowFetch is false.");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		return super.interpret(bill);
	}
	
	@Override
	protected void archiveInterpretation(BillInterpretation interp)
	{
		try {
			File storage = new File(getLocalStorage(), "interpretations");
			File out = new File(storage, interp.getBill().getName() + ".json");
			
			var mapper = new ObjectMapper();
			mapper.writeValue(out, interp);
			
			System.out.println("Wrote OpenAI response to " + out.getAbsolutePath());
		
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
