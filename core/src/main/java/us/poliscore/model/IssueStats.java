package us.poliscore.model;

import java.util.Comparator;
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
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;
import us.poliscore.model.dynamodb.IssueStatsMapAttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;

@Data
@DynamoDbBean
@RegisterForReflection
public class IssueStats {
	
	public static Integer NA = Integer.MIN_VALUE;
	
	protected Map<TrackedIssue, Integer> stats = new HashMap<TrackedIssue, Integer>();
	
	@Getter(onMethod=@__({@JsonIgnore, @DynamoDbIgnore}))
	@JsonIgnore
	protected Map<TrackedIssue, Double> totalSummed;
	
//	public static IssueStats parse(String text)
//	{
//		IssueStats stats = new IssueStats();
//		
//		try (final Scanner scanner = new Scanner(text))
//		{
//			boolean readStats = false;
//			
//			while (scanner.hasNextLine())
//			{
//			  String line = scanner.nextLine().strip();
//			  
//			  Pair<TrackedIssue, Integer> stat = parseStat(line);
//			  
//			  if (stat != null)
//			  {
//				  readStats = true;
//				  if (stat.right() != NA) stats.setStat(stat.left(), stat.right());
//			  }
//			  else if (summaryHeader.contains(line.trim().toLowerCase())) { continue; }
//			  else if (readStats && line.matches(".*[^\\s]{2,}.*"))
//			  {
//				  stats.explanation += line;
//			  }
//			}
//		}
//		
//		val summaryHeaders = new String[] { "summary of the predicted impact to society and why", "summary of the predicted impact to society", "summary of the bill and predicted impact to society and why", "summary of the bill and predicted impact to society", "summary of the bill and its predicted impact to society and why", "summary of the bill and its predicted impact to society", "Summary of the bill's predicted impact to society and why", "Summary of the bill's predicted impact to society", "summary of predicted impact to society and why", "summary of predicted impact to society", "summary of the impact to society", "summary of impact to society", "summary report", "summary of the impact", "summary of impact", "summary", "explanation" };
//		val summaryHeaderRegex = " *#*\\** *(" + String.join("|", summaryHeaders) + ") *#*\\** *:? *#*\\** *";
//		if (stats.explanation.matches("(?i)^" + summaryHeaderRegex + ".*$")) {
//			stats.explanation = stats.explanation.replaceFirst("(?i)" + summaryHeaderRegex, "");
//		}
//		
//		return stats;
//	}
	
	public static Pair<TrackedIssue, Integer> parseStat(String line)
	{
		for (TrackedIssue issue : TrackedIssue.values())
		{
			Pattern pattern = Pattern.compile("^ ?-? ?\\**#*\\d?\\d?\\.?\\**#* ?\\**#*" + issue.getName() + "\\**#* *: *\\**#* *([+-]? *\\d+\\.?\\d*|N\\/A) *\\**#*\\\\* *(\\(.*\\))?$", Pattern.CASE_INSENSITIVE);
			Matcher matcher = pattern.matcher(line);
		    
		    if (matcher.find()) {
		    	Integer value;
		    	if (matcher.group(1).equals("N/A")) {
		    		value = NA;
		    	} else {
		    		value = Integer.parseInt(matcher.group(1).replaceAll("\\s", ""));
		    	}
		    	
		    	return Pair.of(issue, value);
		    }
		}
		
		return null;
	}
	
	@JsonIgnore
	public String getLetterGrade() {
		int credit = this.getRating();
		
		if (credit >= 40) return "A";
		else if (credit >= 30 && credit < 40) return "B";
		else if (credit >= 15 && credit < 30) return "C";
		else if (credit >= 0 && credit < 15) return "D";
		else if (credit < 0) return "F";
		else throw new UnsupportedOperationException("Programming error. Can't handle " + credit);
	}
	
//	@DynamoDbConvertedBy(EnumMapAttributeConverter.class)
//	@DynamoDbIgnore
	@DynamoDbConvertedBy(IssueStatsMapAttributeConverter.class)
	public Map<TrackedIssue, Integer> getStats()
	{
		return this.stats;
	}
	
	@JsonIgnore
	public Integer getStat(TrackedIssue issue)
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
		
		result.totalSummed = sumWeightMap(incoming.asWeightMap(weight));
		
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
	
	public DoubleIssueStats toDoubleIssueStats()
	{
		DoubleIssueStats result = new DoubleIssueStats();
		
		for (TrackedIssue issue : stats.keySet())
		{
			result.setStat(issue, getStat(issue));
		}
		result.totalSummed = totalSummed;
		
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
		
	    sb.append(String.join("\n", stats.keySet().stream().sorted(new IssueStatsComparator()).map(issue ->"-" + issue.getName() + ": " + formatStatValue(getStat(issue))).toList()));
		
		return sb.toString();
	}
	
	private String formatStatValue(int val)
	{
		return (val > 0 ? "+" : "") + String.valueOf(val);
	}

	@JsonIgnore
	public int getRating() {
		return getStat(TrackedIssue.OverallBenefitToSociety);
	}
	
	public class IssueStatsComparator implements Comparator<TrackedIssue> {

	    @Override
	    public int compare(TrackedIssue a, TrackedIssue b) {
	    	if (a.equals(TrackedIssue.OverallBenefitToSociety) && !b.equals(TrackedIssue.OverallBenefitToSociety)) {
	    		return -1;
	    	} else if (!a.equals(TrackedIssue.OverallBenefitToSociety) && b.equals(TrackedIssue.OverallBenefitToSociety)) {
	    		return 1;
	    	} else {
	    		return Integer.valueOf(getStat(b)).compareTo(getStat(a));
	    	}
	    }

	}
}
