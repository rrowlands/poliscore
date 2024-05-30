package ch.poliscore.bill;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;

import ch.poliscore.IssueStats;
import ch.poliscore.TrackedIssue;
import ch.poliscore.service.AIService;
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
    	String billText;
    	try {
			billText = IOUtils.toString(new URL(url), "UTF-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
    	
//    	if (billText.length() >= BillSlicer.MAX_SECTION_LENGTH)
//    	{
    		List<String> sections = new TextBillSlicer().slice(billText);
    		
    		IssueStats billStats = new IssueStats();
    		
    		for (String section : sections)
    		{
    			IssueStats sectionStats = IssueStats.parse(ai.Chat(systemMsg, section));
    		
    			billStats = billStats.sum(sectionStats);
    		}
    		
    		return billStats;
//    	}
//    	else
//    	{
//    		return IssueStats.parse(ai.Chat(systemMsg, billText));
//    	}
    }
}
