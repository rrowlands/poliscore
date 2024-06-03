package ch.poliscore.service;

import java.util.Arrays;
import java.util.List;

import ch.poliscore.IssueStats;
import ch.poliscore.TrackedIssue;
import ch.poliscore.bill.Bill;
import ch.poliscore.bill.BillInterpretation;
import ch.poliscore.bill.OpenAISliceInterpretationMetadata;
import ch.poliscore.bill.OpenAIInterpretationMetadata;
import ch.poliscore.bill.parsing.BillSlice;
import ch.poliscore.bill.parsing.BillSlicer;
import ch.poliscore.bill.parsing.XMLBillSlicer;
import jakarta.inject.Inject;

public class BillInterpretationService {
	
	final String prompt = """
			Score the following bill (or bill section) on the estimated impact upon the following sectors of the United States, rated from -10 (very harmful) to 0 (neutral) to +10 (very helpful). Please format your response as a list in the example format:

            {issuesList}
			
			<brief summary of the predicted impact to society and why>
			""";
	
	final String systemMsg;
	{
		String issues = String.join("\n", Arrays.stream(TrackedIssue.values()).map(issue -> issue.getName() + ": <score>").toList());
    	systemMsg = prompt.replaceFirst("\\{issuesList\\}", issues);
	}
	
	final String summaryPrompt = "A political bill has split into sections and each section has been interpreted. Using the following interpretations, create a summarized interpretation of the bill as a whole.";
	
	@Inject
	protected OpenAIService ai;
	
	public BillInterpretation interpret(Bill bill)
	{
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
		
		return bi;
	}
    
    protected void archiveInterpretation(BillInterpretation interp)
    {
    	// TODO : Dynamodb
    }
}
