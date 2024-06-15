package ch.poliscore.service;

import java.util.Arrays;
import java.util.List;

import ch.poliscore.IssueStats;
import ch.poliscore.TrackedIssue;
import ch.poliscore.bill.OpenAIInterpretationMetadata;
import ch.poliscore.bill.OpenAISliceInterpretationMetadata;
import ch.poliscore.bill.parsing.BillSlice;
import ch.poliscore.bill.parsing.BillSlicer;
import ch.poliscore.bill.parsing.XMLBillSlicer;
import ch.poliscore.model.Bill;
import ch.poliscore.model.BillInterpretation;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
	protected S3PersistenceService s3;
	
	public BillInterpretation interpret(Bill bill)
	{
		Log.info("Interpreting bill " + bill.getId() + " " + bill.getName());
		
		if (bill.getText().length() >= BillSlicer.MAX_SECTION_LENGTH)
    	{
    		List<BillSlice> slices = new XMLBillSlicer().slice(bill);
    		
    		if (slices.size() > 1)
    		{
	    		IssueStats billStats = new IssueStats();
	    		
	    		for (int i = 0; i < slices.size(); ++i)
	    		{
	    			BillSlice slice = slices.get(i);
	    			
	    			BillInterpretation sliceInterp = fetchInterpretation(bill, slice);
	    			
	    			archiveInterpretation(sliceInterp);
	    			
	    			billStats = billStats.sum(sliceInterp.getIssueStats());
	    		}
	    		
	    		billStats = billStats.divide(slices.size()); // Dividing by the total slices here gives us an average
	    		
 	    		var bi = fetchAggregateInterpretation(bill, billStats);
	    		
	    		System.out.println(bi.getIssueStats().toString());	    		
	    		archiveInterpretation(bi);
	    		
	    		return bi;
    		}
    	}
		
		var bi = fetchInterpretation(bill, null);
		
		archiveInterpretation(bi);
    	
    	return bi;
	}
	
	protected BillInterpretation fetchAggregateInterpretation(Bill bill, IssueStats aggregateStats)
	{
		BillInterpretation bi = new BillInterpretation();
		bi.setBill(bill);
		bi.setMetadata(OpenAIInterpretationMetadata.construct());
		
		aggregateStats.explanation = ai.chat(summaryPrompt, aggregateStats.explanation);
		
		bi.setText(aggregateStats.toString());
		bi.calculateId();
		
		return bi;
	}
	
	protected BillInterpretation fetchInterpretation(Bill bill, BillSlice slice)
	{
		BillInterpretation bi = new BillInterpretation();
		bi.setBill(bill);
		
		String interpText;
		if (slice == null)
		{
			interpText = ai.chat(systemMsg, bill.getText());
			bi.setMetadata(OpenAIInterpretationMetadata.construct());
		}
		else
		{
			interpText = ai.chat(systemMsg, slice.getText());
			bi.setMetadata(OpenAISliceInterpretationMetadata.construct(slice));
		}
		
		bi.setText(interpText);
		bi.calculateId();
		
		return bi;
	}
    
    protected void archiveInterpretation(BillInterpretation interp)
    {
    	s3.store(interp);
    }
}
