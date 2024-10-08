package us.poliscore;

import org.junit.jupiter.api.Assertions;

import us.poliscore.model.IssueStats;
import us.poliscore.model.TrackedIssue;

public class IssueStatsTest {
//	@Test
	public void testSumStats()
	{
		IssueStats stats = buildStats();
		IssueStats stats2 = buildStats();
		
		IssueStats summedStats = stats.sum(stats2);
		
		for (TrackedIssue issue : TrackedIssue.values())
		{
			Assertions.assertEquals(2, summedStats.getStat(issue));
		}
	}
	
//	@Test
	public void testParseStats()
	{
//		IssueStats stats = buildStats();
		
//		System.out.println(IssueStats.parse(stats.toString()));
	}
	
	private IssueStats buildStats()
	{
		IssueStats stats = new IssueStats();
		for (TrackedIssue issue : TrackedIssue.values())
		{
			stats.addStat(issue, 1);
		}
		return stats;
	}
}
