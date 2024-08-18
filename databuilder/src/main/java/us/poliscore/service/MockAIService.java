package us.poliscore.service;

import us.poliscore.model.IssueStats;
import us.poliscore.model.TrackedIssue;

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
		return stats.toString();
	}
	
	protected String buildAggregateResponse()
	{
		return "This is a mocked summary of a bill, produced by various bill slice interpretations.";
	}
}
