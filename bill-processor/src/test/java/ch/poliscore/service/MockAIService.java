package ch.poliscore.service;

import ch.poliscore.IssueStats;
import ch.poliscore.TrackedIssue;
import io.quarkus.test.Mock;

@Mock
public class MockAIService extends OpenAIService {
	public String chat(String systemMsg, String userMsg)
    {
		IssueStats stats = new IssueStats();
		for (TrackedIssue issue : TrackedIssue.values())
		{
			stats.addStat(issue, 1);
		}
		stats.explanation = "Test Explanation 1.";
		return stats.toString();
    }
}
