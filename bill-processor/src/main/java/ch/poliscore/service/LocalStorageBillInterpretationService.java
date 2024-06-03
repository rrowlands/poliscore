package ch.poliscore.service;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.poliscore.Environment;
import ch.poliscore.IssueStats;
import ch.poliscore.bill.Bill;
import ch.poliscore.bill.BillInterpretation;
import ch.poliscore.bill.OpenAISliceInterpretationMetadata;
import ch.poliscore.bill.OpenAIInterpretationMetadata;
import ch.poliscore.bill.parsing.BillSlice;
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
	
//	@Override
//	public BillInterpretation interpret(Bill bill) {
//		try {
//			File interpretStorage = new File(getLocalStorage(), "interpretations");
//			File stored = new File(interpretStorage, bill.getName() + ".json");
//			
//			if (stored.exists())
//			{
//				var mapper = new ObjectMapper();
//				BillInterpretation interp = mapper.readValue(stored, BillInterpretation.class);
//				interp.setBill(billService.fetchBill(interp.getBillUrl()));
//				return interp;
//			}
//			
//			if (!allowFetch()) throw new RuntimeException("Could not find interpretation in local storage at " + stored.getAbsolutePath() + " and allowFetch is false.");
//		} catch (IOException e) {
//			throw new RuntimeException(e);
//		}
//		
//		return super.interpret(bill);
//	}
	
	@Override
	protected BillInterpretation fetchAggregateInterpretation(Bill bill, IssueStats aggregateStats)
	{
		var bi = this.fetchInterpretation(bill, null);
		
		if (bi != null)
		{
			bi.setMetadata(OpenAISliceInterpretationMetadata.construct());
			
			return bi;
		}
		
		return super.fetchAggregateInterpretation(bill, aggregateStats);
	}
	
	@Override
	protected BillInterpretation fetchInterpretation(Bill bill, BillSlice slice)
	{
		try {
			File interpretStorage = new File(getLocalStorage(), "interpretations");
			
			String name = (slice == null) ? bill.getName() : bill.getName() + "-" + slice.getSliceIndex();
			File stored = new File(interpretStorage, name + ".json");
			
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
		
		return super.fetchInterpretation(bill, slice);
	}
	
	@Override
	protected void archiveInterpretation(BillInterpretation interp)
	{
		try {
			File storage = new File(getLocalStorage(), "interpretations");
			File out = new File(storage, interp.getName() + ".json");
			
			var mapper = new ObjectMapper();
			mapper.writerWithDefaultPrettyPrinter().writeValue(out, interp);
			
			System.out.println("Wrote OpenAI response to " + out.getAbsolutePath());
		
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
