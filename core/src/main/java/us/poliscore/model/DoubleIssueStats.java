package us.poliscore.model;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import lombok.Getter;
import lombok.val;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;

@Data
@RegisterForReflection
public class DoubleIssueStats {
	
	public static Integer NA = Integer.MIN_VALUE;
	
	protected Map<TrackedIssue, Double> stats = new HashMap<TrackedIssue, Double>();
	
	@Getter(onMethod=@__({@JsonIgnore, @DynamoDbIgnore}))
	@JsonIgnore
	protected Map<TrackedIssue, Double> totalSummed;
	
	@JsonIgnore
	public String getLetterGrade() {
		double credit = this.getRating();
		
		if (credit >= 40) return "A";
		else if (credit >= 30 && credit < 40) return "B";
		else if (credit >= 15 && credit < 30) return "C";
		else if (credit >= 0 && credit < 15) return "D";
		else if (credit < 0) return "F";
		else throw new UnsupportedOperationException("Programming error. Can't handle " + credit);
	}
	
	public Map<TrackedIssue, Double> getStats()
	{
		return this.stats;
	}
	
	@JsonIgnore
	public double getStat(TrackedIssue issue)
	{
		return getStat(issue, 0);
	}
	
	@JsonIgnore
	public double getStat(TrackedIssue issue, double defaultValue)
	{
		return stats.getOrDefault(issue, defaultValue);
	}
	
	public boolean hasStat(TrackedIssue issue)
	{
		return stats.containsKey(issue);
	}
	
	public void setStat(TrackedIssue issue, double value)
	{
		stats.put(issue, value);
	}
	
	public void addStat(TrackedIssue issue, double value)
	{
		stats.put(issue, getStat(issue) + value);
	}
	
	public DoubleIssueStats sum(DoubleIssueStats incoming)
	{
		return sum(incoming, 1.0f);
	}
	
	public DoubleIssueStats sum(DoubleIssueStats incoming, float weight)
	{
		DoubleIssueStats result = new DoubleIssueStats();
		
		for (TrackedIssue issue : TrackedIssue.values())
		{
			if (!incoming.hasStat(issue) && !hasStat(issue)) continue;
			
			result.setStat(issue, getStat(issue) + incoming.getStat(issue));
		}
		
		result.totalSummed = sumWeightMap(incoming.asWeightMap(weight));
		
		return result;
	}
	
	public IssueStats toIssueStats()
	{
		IssueStats result = new IssueStats();
		
		for (TrackedIssue issue : stats.keySet())
		{
			result.setStat(issue, (int) Math.round(getStat(issue)));
		}
		result.totalSummed = totalSummed;
		
		return result;
	}
	
	public DoubleIssueStats divide(double divisor)
	{
		DoubleIssueStats result = new DoubleIssueStats();
		
		for (TrackedIssue issue : stats.keySet())
		{
			result.setStat(issue, (double)getStat(issue) / divisor);
		}
		
		return result;
	}
	
	public DoubleIssueStats divideByTotalSummed()
	{
		DoubleIssueStats result = new DoubleIssueStats();
		
		for (TrackedIssue issue : stats.keySet())
		{
			if (!totalSummed.containsKey(issue)) throw new NoSuchElementException();
			
			result.setStat(issue, (double)getStat(issue) / totalSummed.get(issue));
		}
		
		return result;
	}
	
	public DoubleIssueStats multiply(double multiplier)
	{
		DoubleIssueStats result = new DoubleIssueStats();
		
		for (TrackedIssue issue : stats.keySet())
		{
			result.setStat(issue, (double)getStat(issue) * multiplier);
		}
		
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
		
	    sb.append(String.join("\n", stats.keySet().stream().map(issue ->"-" + issue.getName() + ": " + formatStatValue(getStat(issue))).toList()));
		
		return sb.toString();
	}
	
	private String formatStatValue(double val)
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

	@JsonIgnore
	public double getRating() {
		return getStat(TrackedIssue.OverallBenefitToSociety);
	}
}
