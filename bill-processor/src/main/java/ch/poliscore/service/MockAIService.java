package ch.poliscore.service;

import ch.poliscore.TrackedIssue;
import ch.poliscore.model.IssueStats;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;

//@ApplicationScoped
//@Priority(1)
public class MockAIService extends OpenAIService {
	
	private int num = 1;
	
	public String chat(String systemMsg, String userMsg)
    {
//		return buildAggregateResponse();
//		return userMsg;
		
		return buildIssueStatsResponse();
    }
	
	protected String buildIssueStatsResponse()
	{
		IssueStats stats = new IssueStats();
		for (TrackedIssue issue : TrackedIssue.values())
		{
			stats.addStat(issue, num);
		}
		stats.setExplanation("Test Explanation " + num++);
		return stats.toString();
	}
	
	protected String buildAggregateResponse()
	{
		return "This is a mocked summary of a bill, produced by various bill slice interpretations.";
	}
}
