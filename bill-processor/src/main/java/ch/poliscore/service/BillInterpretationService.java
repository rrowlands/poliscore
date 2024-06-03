package ch.poliscore.service;

import java.util.Arrays;
import java.util.List;

import ch.poliscore.IssueStats;
import ch.poliscore.TrackedIssue;
import ch.poliscore.bill.Bill;
import ch.poliscore.bill.BillInterpretation;
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
	
	final String summaryPrompt = "A political bill has split into sections each section has been interpreted. Using the following interpretations, create a summarized interpretation of the bill as a whole.";
	
	@Inject
	protected OpenAIService ai;
	
	public BillInterpretation interpret(Bill bill)
	{
		BillInterpretation bi = new BillInterpretation();
		bi.setMetadata(ai.getInterpretationMetadata());
		bi.setBill(bill);
		
		if (bill.getText().length() >= BillSlicer.MAX_SECTION_LENGTH)
    	{
    		List<BillSlice> sections = new XMLBillSlicer().slice(bill);
    		
    		IssueStats billStats = new IssueStats();
    		
    		for (int i = 0; i < sections.size(); ++i)
    		{
    			BillSlice section = sections.get(i);
    			
    			String interpText = ai.Chat(systemMsg, section.getText());
            	IssueStats sectionStats = IssueStats.parse(interpText);
    		
    			billStats = billStats.sum(sectionStats);
    		}
    		
    		billStats.explanation = ai.Chat(summaryPrompt, billStats.explanation);
    		
    		bi.setText(billStats.toString());
    		
    		archiveInterpretation(bi);
    		
    		return bi;
    	}
    	else
    	{
    		String interpText = ai.Chat(systemMsg, bill.getText());
    		
    		bi.setText(interpText);
        	
        	archiveInterpretation(bi);
        	
        	return bi;
    	}
	}
    
    protected void archiveInterpretation(BillInterpretation interp)
    {
    	// TODO : Dynamodb
    }
}
