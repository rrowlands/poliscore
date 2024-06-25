package us.poliscore.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import us.poliscore.bill.parsing.BillSlice;
import us.poliscore.bill.parsing.BillSlicer;
import us.poliscore.bill.parsing.XMLBillSlicer;
import us.poliscore.interpretation.OpenAIInterpretationMetadata;
import us.poliscore.interpretation.OpenAISliceInterpretationMetadata;
import us.poliscore.model.Bill;
import us.poliscore.model.BillInterpretation;
import us.poliscore.model.IssueStats;
import us.poliscore.model.TrackedIssue;
import us.poliscore.service.storage.MemoryPersistenceService;
import us.poliscore.service.storage.S3PersistenceService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.val;

@ApplicationScoped
public class BillInterpretationService {
	
	final String prompt = """
			Score the following bill (or bill section) on the estimated impact upon the following criteria, rated from -100 (very harmful) to 0 (neutral) to +100 (very helpful). Include a concise (single paragraph) summary of the bill at the end which references concrete, notable and specific text of the summarized bill where possible. Please format your response as a list in the example format:

            {issuesList}
			
			<brief summary of the predicted impact to society and why>
			""";
	
	final String systemMsg;
	{
		String issues = String.join("\n", Arrays.stream(TrackedIssue.values()).map(issue -> issue.getName() + ": <score>").toList());
    	systemMsg = prompt.replaceFirst("\\{issuesList\\}", issues);
	}
	
	final String summaryPrompt = "Generate a concise (single paragraph) summary of the following text. In your summary, please attempt to reference concrete, notable and specific text of the summarized bill where possible.";
	
	@Inject
	protected OpenAIService ai;
	
	@Inject
	protected MemoryPersistenceService pService;
	
	@Inject
	protected S3PersistenceService s3;
	
	@Inject
	protected BillService billService;
	
	public Optional<BillInterpretation> getById(String billId)
	{
		return s3.retrieve(BillInterpretation.generateId(billId, null), BillInterpretation.class);
	}
	
	public BillInterpretation getOrCreate(String billId)
	{
		val bill = billService.getById(billId).orElseThrow();
		val interpId = BillInterpretation.generateId(bill.getId(), null);
		val cached = s3.retrieve(interpId, BillInterpretation.class);
		
		if (cached.isPresent())
		{
			return cached.get();
		}
		else
		{
			val interp = interpret(bill);
			
			return interp;
		}
	}
	
	protected BillInterpretation interpret(Bill bill)
	{
		Log.info("Interpreting bill " + bill.getId() + " " + bill.getName());
		
		val billText = billService.getBillText(bill).orElseThrow();
		
		bill.setText(billText);
		
		if (billText.getXml().length() >= BillSlicer.MAX_SECTION_LENGTH)
    	{
    		List<BillSlice> slices = new XMLBillSlicer().slice(bill);
    		
    		if (slices.size() > 1)
    		{
	    		IssueStats billStats = new IssueStats();
	    		
	    		for (int i = 0; i < slices.size(); ++i)
	    		{
	    			BillSlice slice = slices.get(i);
	    			
	    			BillInterpretation sliceInterp = getOrCreateInterpretation(bill, slice);
	    			
	    			billStats = billStats.sum(sliceInterp.getIssueStats());
	    		}
	    		
	    		billStats = billStats.divide(slices.size()); // Dividing by the total slices here gives us an average
	    		
 	    		var bi = getOrCreateAggregateInterpretation(bill, billStats);
	    		
	    		return bi;
    		}
    	}
		
		var bi = getOrCreateInterpretation(bill, null);
		
    	return bi;
	}
	
	protected BillInterpretation getOrCreateAggregateInterpretation(Bill bill, IssueStats aggregateStats)
	{
		BillInterpretation bi = new BillInterpretation();
		bi.setBill(bill);
		bi.setMetadata(OpenAIInterpretationMetadata.construct());
		
		aggregateStats.setExplanation(ai.chat(summaryPrompt, aggregateStats.getExplanation()));
		
		bi.setText(aggregateStats.toString());
		bi.setId(BillInterpretation.generateId(bill.getId(), null));
		
		archive(bi);
		
		return bi;
	}
	
	protected BillInterpretation getOrCreateInterpretation(Bill bill, BillSlice slice)
	{
		val id = BillInterpretation.generateId(bill.getId(), slice == null ? null : slice.getSliceIndex());
		val cached = s3.retrieve(id, BillInterpretation.class);
		
		if (cached.isPresent())
		{
			return cached.get();
		}
		else
		{
			BillInterpretation bi = new BillInterpretation();
			bi.setBill(bill);
			
			String interpText;
			if (slice == null)
			{
				interpText = ai.chat(systemMsg, bill.getText().getXml());
				bi.setMetadata(OpenAIInterpretationMetadata.construct());
			}
			else
			{
				interpText = ai.chat(systemMsg, slice.getText());
				bi.setMetadata(OpenAISliceInterpretationMetadata.construct(slice));
			}
			
			bi.setText(interpText);
			bi.setId(id);
			
			archive(bi);
			
			return bi;
		}
	}
	
    protected void archive(BillInterpretation interp)
    {
    	s3.store(interp);
    	pService.store(interp);
    }
}
