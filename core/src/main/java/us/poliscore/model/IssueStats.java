package us.poliscore.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import lombok.val;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.utils.Pair;

@Data
@DynamoDbBean
@RegisterForReflection
public class IssueStats {
	
	protected static Integer NA = Integer.MIN_VALUE;
	
	protected static List<String> summaryHeader = Arrays.asList("summary:", "*summary:*", "**summary:**", "*summary*", "**summary**");
	
	protected Map<TrackedIssue, Integer> stats = new HashMap<TrackedIssue, Integer>();
	
	protected String explanation = "";
	
	protected Map<TrackedIssue, Double> totalSummed;
	
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
				  if (stat.right() != NA) stats.setStat(stat.left(), stat.right());
			  }
			  else if (summaryHeader.contains(line.trim().toLowerCase())) { continue; }
			  else if (readStats && line.matches(".*[^\\s]{2,}.*"))
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
			Pattern pattern = Pattern.compile(issue.getName() + ": ([+-]?\\d+.?\\d*|N\\/A)", Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(line);
		    
		    if (matcher.find()) {
		    	Integer value;
		    	if (matcher.group(1).equals("N/A")) {
		    		value = NA;
		    	} else {
		    		value = Integer.parseInt(matcher.group(1));
		    	}
		    	
		    	return Pair.of(issue, value);
		    }
		}
		
		return null;
	}
	
//	@DynamoDbConvertedBy(EnumMapAttributeConverter.class)
//	@DynamoDbIgnore
	@DynamoDbConvertedBy(IssueStatsMapAttributeConverter.class)
	public Map<TrackedIssue, Integer> getStats()
	{
		return this.stats;
	}
	
	@JsonIgnore
	public int getStat(TrackedIssue issue)
	{
		return getStat(issue, 0);
	}
	
	@JsonIgnore
	public int getStat(TrackedIssue issue, int defaultValue)
	{
		return stats.getOrDefault(issue, defaultValue);
	}
	
	public boolean hasStat(TrackedIssue issue)
	{
		return stats.containsKey(issue);
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
		return sum(incoming, 1.0f);
	}
	
	public IssueStats sum(IssueStats incoming, float weight)
	{
		IssueStats result = new IssueStats();
		
		for (TrackedIssue issue : TrackedIssue.values())
		{
			if (!incoming.hasStat(issue) && !hasStat(issue)) continue;
			
			result.setStat(issue, getStat(issue) + incoming.getStat(issue));
		}
		
		result.explanation = explanation + "\n" + incoming.explanation;
		
		result.totalSummed = sumWeightMap(incoming.asWeightMap(weight));
		
		return result;
	}
	
	public IssueStats divide(double divisor)
	{
		IssueStats result = new IssueStats();
		
		for (TrackedIssue issue : stats.keySet())
		{
			result.setStat(issue, (int) Math.round((double)getStat(issue) / divisor));
		}
		
		result.explanation = explanation;
		
		return result;
	}
	
	public IssueStats divideByTotalSummed()
	{
		IssueStats result = new IssueStats();
		
		for (TrackedIssue issue : stats.keySet())
		{
			if (!totalSummed.containsKey(issue)) throw new NoSuchElementException();
			
			result.setStat(issue, (int) Math.round((double)getStat(issue) / totalSummed.get(issue)));
		}
		
		result.explanation = explanation;
		
		return result;
	}
	
	public IssueStats multiply(double multiplier)
	{
		IssueStats result = new IssueStats();
		
		for (TrackedIssue issue : stats.keySet())
		{
			result.setStat(issue, (int) Math.round((double)getStat(issue) * multiplier));
		}
		
		result.explanation = explanation;
		
		return result;
	}
	
	protected Map<TrackedIssue, Double> asWeightMap(double weight)
	{
		val result = new HashMap<TrackedIssue, Double>();
		
		for (TrackedIssue issue : stats.keySet())
		{
			result.put(issue, weight);
		}
		
		return result;
	}
	
	protected Map<TrackedIssue, Double> sumWeightMap(Map<TrackedIssue, Double> incoming)
	{
		if (totalSummed == null) { return incoming; }
		
		val result = new HashMap<TrackedIssue, Double>();
		
		for (TrackedIssue issue : TrackedIssue.values())
		{
			if (!incoming.containsKey(issue) && !totalSummed.containsKey(issue)) continue;
			
			result.put(issue, incoming.getOrDefault(issue, 0d) + totalSummed.getOrDefault(issue, 0d));
		}
		
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
	
	private String formatStatValue(int val)
	{
		final String sign = (val > 0) ? "+" : (val < 0 ? "-" : "");
		
//		if (Double.valueOf(Math.floor(val)).equals(Double.valueOf(val)))
//		{
//			return sign + String.valueOf((int)val);
//		}
//		
//		return sign + String.format("%.1f", val);
		
		return sign + String.valueOf(val);
	}
}
