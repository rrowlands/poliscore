package ch.poliscore.service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;

import ch.poliscore.IssueStats;
import ch.poliscore.TrackedIssue;
import ch.poliscore.bill.BillSlicer;
import ch.poliscore.bill.TextBillSlicer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class BillProcessingService {

	@Inject
	private AIService ai;
	
	final String prompt = """
			Please score the following bill (or bill section) on the estimated impact upon the following sectors of the United States, rated from -10 (very harmful) to 0 (neutral) to +10 (very helpful). Please format your response as a list in the example format:

            {issuesList}
			
			<brief summary of the predicted impact to society and why>
			""";
	
	final String systemMsg;
	{
		String issues = String.join("\n", Arrays.stream(TrackedIssue.values()).map(issue -> issue.getName() + ": <score>").toList());
    	systemMsg = prompt.replaceFirst("\\{issuesList\\}", issues);
	}
	
    public IssueStats process(String url) {
    	String billText = fetchBillText(url);
    	
    	if (billText.length() >= BillSlicer.MAX_SECTION_LENGTH)
    	{
    		List<String> sections = new TextBillSlicer().slice(billText);
    		
    		IssueStats billStats = new IssueStats();
    		
    		for (int i = 0; i < sections.size(); ++i)
    		{
    			String interpretation = interpret(sections.get(i), url, i);
    			
    			IssueStats sectionStats = IssueStats.parse(interpretation);
    		
    			billStats = billStats.sum(sectionStats);
    		}
    		
    		return billStats;
    	}
    	else
    	{
    		return IssueStats.parse(interpret(billText, url, 0));
    	}
    }
    
    protected String billNameFromUrl(String url)
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
    	
    	archiveBillText(billText, url);
    	
    	return billText;
    }
    
    protected String interpret(String text, String bill, int sliceIndex)
    {
    	String airesp = ai.Chat(systemMsg, text);
    	
    	archiveInterpretation(airesp, bill, sliceIndex);
    	
    	return airesp;
    }
    
    protected void archiveInterpretation(String interp, String bill, int sliceIndex)
    {
    	// TODO : Dynamodb
    }
    
    protected void archiveBillText(String text, String bill)
    {
    	// TODO : Dynamodb
    }
}
