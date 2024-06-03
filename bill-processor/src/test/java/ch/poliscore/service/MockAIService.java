package ch.poliscore.service;

import ch.poliscore.IssueStats;
import ch.poliscore.TrackedIssue;
import io.quarkus.test.Mock;

@Mock
public class MockAIService extends OpenAIService {
	public String chat(String systemMsg, String userMsg)
    {
		return buildAggregateResponse();
    }
	
	protected String buildIssueStatsResponse()
	{
		IssueStats stats = new IssueStats();
		for (TrackedIssue issue : TrackedIssue.values())
		{
			stats.addStat(issue, 1);
		}
		stats.explanation = "Test Explanation 1.";
		return stats.toString();
	}
	
	protected String buildAggregateResponse()
	{
		return "This is a mocked summary of a bill, produced by various bill slice interpretations.";
	}
}
