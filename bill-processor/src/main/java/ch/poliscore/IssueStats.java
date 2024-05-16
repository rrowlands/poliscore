package ch.poliscore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import software.amazon.awssdk.utils.Pair;

public class IssueStats {
	
	public Map<TrackedIssue, Integer> stats = new HashMap<TrackedIssue, Integer>();
	
	public String explanation;
	
	public static IssueStats parse(String text)
	{
		IssueStats stats = new IssueStats();
		
		try (final Scanner scanner = new Scanner(text))
		{
			boolean readStats = false;
			
			while (scanner.hasNextLine())
			{
			  String line = scanner.nextLine();
			  
			  Pair<TrackedIssue, Integer> stat = parseStat(line);
			  
			  if (stat != null)
			  {
				  readStats = true;
			  }
			  else if (readStats && line.matches("[^\\s]{2,}"))
			  {
				  stats.explanation += line;
			  }
			}
		}
		
		return stats;
	}
	
	private static Pair<TrackedIssue, Integer> parseStat(String line)
	{
		for (TrackedIssue issue : TrackedIssue.values())
		{
			Pattern pattern = Pattern.compile(issue.getName() + ": ([+-]?\\d+)", Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(line);
		    
		    if (matcher.find()) {
		    	Integer value = Integer.parseInt(matcher.group(1));
		    	
		    	return Pair.of(issue, value);
		    }
		}
		
		return null;
	}
	
	public int getStat(TrackedIssue issue)
	{
		return stats.getOrDefault(issue, 0);
	}
	
	public void setStat(TrackedIssue issue, int value)
	{
		stats.put(issue, value);
	}
	
	public void addStat(TrackedIssue issue, int value)
	{
		stats.put(issue, getStat(issue) + value);
	}
	
	public IssueStats sum(IssueStats incoming)
	{
		IssueStats result = new IssueStats();
		
		for (TrackedIssue issue : TrackedIssue.values())
		{
			result.addStat(issue, incoming.getStat(issue));
		}
		
		return result;
	}
	
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		
	    sb.append(String.join("\n", Arrays.stream(TrackedIssue.values()).map(issue ->"-" + issue.getName() + ": " + getStat(issue)).toList()));
		
		sb.append("\n\n");
		
		sb.append(explanation);
		
		return sb.toString();
	}
}
