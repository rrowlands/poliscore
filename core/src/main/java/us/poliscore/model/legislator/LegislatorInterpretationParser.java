package us.poliscore.model.legislator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.lang3.tuple.Pair;

import us.poliscore.model.IssueStats;
import us.poliscore.model.TrackedIssue;

public class LegislatorInterpretationParser {
	
	public static Integer NA = Integer.MIN_VALUE;
	
	public static List<String> summaryHeader = Arrays.asList("summary:", "*summary:*", "**summary:**", "*summary*", "**summary**");
	
	public static String[] summaryHeaders = new String[] { "summary of the predicted impact to society and why", "summary of the predicted impact to society", "summary of the bill and predicted impact to society and why", "summary of the bill and predicted impact to society", "summary of the bill and its predicted impact to society and why", "summary of the bill and its predicted impact to society", "Summary of the bill's predicted impact to society and why", "Summary of the bill's predicted impact to society", "summary of predicted impact to society and why", "summary of predicted impact to society", "summary of the impact to society", "summary of impact to society", "summary report", "summary of the impact", "summary of impact", "summary", "explanation" };
	public static String summaryHeaderRegex = " *#*\\** *(" + String.join("|", summaryHeaders) + ") *#*\\** *:? *#*\\** *";
	
	private LegislatorInterpretation interp;
	
	public LegislatorInterpretationParser(LegislatorInterpretation interp) {
		this.interp = interp;
	}
	
	public void parse(String text) {
		IssueStats stats = new IssueStats();
		List<String> summaryLines = new ArrayList<String>();
		
		try (final Scanner scanner = new Scanner(text))
		{
			boolean readStats = false;
			
			while (scanner.hasNextLine())
			{
			  String line = scanner.nextLine().strip();
			  
			  Pair<TrackedIssue, Integer> stat = IssueStats.parseStat(line);
			  
			  if (stat != null)
			  {
				  readStats = true;
				  if (stat.getRight() != NA) stats.setStat(stat.getLeft(), stat.getRight());
			  }
			  else if (summaryHeader.contains(line.trim().toLowerCase())) { continue; }
			  else if (readStats && line.matches(".*[^\\s]{2,}.*"))
			  {
				  summaryLines.add(line);
			  }
			}
		}
		
		interp.setIssueStats(stats);
		interp.setLongExplain(String.join("\n", summaryLines));
		
		if (interp.getLongExplain().matches("(?i)^" + summaryHeaderRegex + ".*$")) {
			interp.setLongExplain(interp.getLongExplain().replaceFirst("(?i)" + summaryHeaderRegex, ""));
		}
	}
	
}
