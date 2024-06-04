package ch.poliscore;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import software.amazon.awssdk.utils.Pair;

public class IssueStats {
	
	public Map<TrackedIssue, Float> stats = new HashMap<TrackedIssue, Float>();
	
	public String explanation = "";
	
	public static IssueStats parse(String text)
	{
		IssueStats stats = new IssueStats();
		
		try (final Scanner scanner = new Scanner(text))
		{
			boolean readStats = false;
			
			while (scanner.hasNextLine())
			{
			  String line = scanner.nextLine();
			  
			  Pair<TrackedIssue, Float> stat = parseStat(line);
			  
			  if (stat != null)
			  {
				  readStats = true;
				  stats.setStat(stat.left(), stat.right());
			  }
			  else if (readStats && line.matches(".*[^\\s]{2,}.*"))
			  {
				  stats.explanation += line;
			  }
			}
		}
		
		return stats;
	}
	
	private static Pair<TrackedIssue, Float> parseStat(String line)
	{
		for (TrackedIssue issue : TrackedIssue.values())
		{
			Pattern pattern = Pattern.compile(issue.getName() + ": ([+-]?\\d+.?\\d*)", Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(line);
		    
		    if (matcher.find()) {
		    	Float value = Float.parseFloat(matcher.group(1));
		    	
		    	return Pair.of(issue, value);
		    }
		}
		
		return null;
	}
	
	public float getStat(TrackedIssue issue)
	{
		return stats.getOrDefault(issue, 0.0f);
	}
	
	public void setStat(TrackedIssue issue, float value)
	{
		stats.put(issue, value);
	}
	
	public void addStat(TrackedIssue issue, float value)
	{
		stats.put(issue, getStat(issue) + value);
	}
	
	public IssueStats sum(IssueStats incoming)
	{
		IssueStats result = new IssueStats();
		
		for (TrackedIssue issue : TrackedIssue.values())
		{
			result.setStat(issue, getStat(issue) + incoming.getStat(issue));
		}
		
		result.explanation = explanation + "\n" + incoming.explanation;
		
		return result;
	}
	
	public IssueStats divide(float divisor)
	{
		IssueStats result = new IssueStats();
		
		for (TrackedIssue issue : TrackedIssue.values())
		{
			result.setStat(issue, getStat(issue) / divisor);
		}
		
		result.explanation = explanation;
		
		return result;
	}
	
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		
	    sb.append(String.join("\n", Arrays.stream(TrackedIssue.values()).map(issue ->"-" + issue.getName() + ": " + formatStatValue(getStat(issue))).toList()));
		
		sb.append("\n\n");
		
		sb.append(explanation);
		
		return sb.toString();
	}
	
	private String formatStatValue(float val)
	{
		final String sign = (val > 0) ? "+" : (val < 0 ? "-" : "");
		
		if (Double.valueOf(Math.floor(val)).equals(Double.valueOf(val)))
		{
			return sign + String.valueOf((int)val);
		}
		
		return sign + String.format("%.1f", val);
	}
}
