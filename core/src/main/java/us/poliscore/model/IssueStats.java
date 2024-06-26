package us.poliscore.model;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import software.amazon.awssdk.utils.Pair;

@Data
@DynamoDbBean
@RegisterForReflection
public class IssueStats {
	
	protected Map<TrackedIssue, Integer> stats = new HashMap<TrackedIssue, Integer>();
	
	protected String explanation = "";
	
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
	
	private static Pair<TrackedIssue, Integer> parseStat(String line)
	{
		for (TrackedIssue issue : TrackedIssue.values())
		{
			Pattern pattern = Pattern.compile(issue.getName() + ": ([+-]?\\d+.?\\d*)", Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(line);
		    
		    if (matcher.find()) {
		    	Integer value = Integer.parseInt(matcher.group(1));
		    	
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
			result.setStat(issue, getStat(issue) + incoming.getStat(issue));
		}
		
		result.explanation = explanation + "\n" + incoming.explanation;
		
		return result;
	}
	
	public IssueStats divide(double divisor)
	{
		IssueStats result = new IssueStats();
		
		for (TrackedIssue issue : TrackedIssue.values())
		{
			result.setStat(issue, (int) Math.round((double)getStat(issue) / divisor));
		}
		
		result.explanation = explanation;
		
		return result;
	}
	
	public IssueStats multiply(double multiplier)
	{
		IssueStats result = new IssueStats();
		
		for (TrackedIssue issue : TrackedIssue.values())
		{
			result.setStat(issue, (int) Math.round((double)getStat(issue) * multiplier));
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
