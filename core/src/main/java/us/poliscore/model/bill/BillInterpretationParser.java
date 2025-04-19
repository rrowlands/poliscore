package us.poliscore.model.bill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import us.poliscore.model.IssueStats;
import us.poliscore.model.TrackedIssue;

public class BillInterpretationParser {
	
	public static List<String> summaryHeader = Arrays.asList("summary:", "*summary:*", "**summary:**", "*summary*", "**summary**");
	
	private State state = null;
	
	private BillInterpretation interp;
	
	public static enum State {
		STATS("(?i)Stats:"),
		TITLE("(?i)Title:", "(?i)Bill Title:"),
		RIDERS("(?i)Riders:"),
		SHORT_REPORT("(?i)Short Report:"),
		LONG_REPORT("(?i)Long Report:");
		
		private List<String> regex;
		
		private State(String ...regex) {
			this.regex = Arrays.asList(regex);
		}
		
//		public boolean matches(String line) {
//			return regex.stream().map(r -> line.matches(r)).reduce(false, (a,b) -> a || b);
//		}
	}
	
	public BillInterpretationParser(BillInterpretation interp) {
		this.interp = interp;
	}
	
	public void parse(String text) {
		interp.setShortExplain("");
		interp.setLongExplain("");
		interp.setRiders(new ArrayList<String>());
		interp.setIssueStats(new IssueStats());
		
		try (final Scanner scanner = new Scanner(text))
		{
			while (scanner.hasNextLine())
			{
			  String line = scanner.nextLine().strip();
			  
			  if (StringUtils.isBlank(line) || setState(line) || state == null) continue;
			  
			  processContent(line);
			}
		}
		
		
		// TODO : Clean?
	}
	
	private void processContent(String line) {
		if (State.STATS.equals(state)) {
			processStat(line);
		} else if (State.TITLE.equals(state)) {
			processTitle(line);
		} else if (State.RIDERS.equals(state)) {
			processRider(line);
		} else if (State.SHORT_REPORT.equals(state)) {
			processShortForm(line);
		} else if (State.LONG_REPORT.equals(state)) {
			processLongForm(line);
		}
	}
	
	private void clean(String dirty) {
//		val summaryHeaders = new String[] { "summary of the predicted impact to society and why", "summary of the predicted impact to society", "summary of the bill and predicted impact to society and why", "summary of the bill and predicted impact to society", "summary of the bill and its predicted impact to society and why", "summary of the bill and its predicted impact to society", "Summary of the bill's predicted impact to society and why", "Summary of the bill's predicted impact to society", "summary of predicted impact to society and why", "summary of predicted impact to society", "summary of the impact to society", "summary of impact to society", "summary report", "summary of the impact", "summary of impact", "summary", "explanation" };
//		val summaryHeaderRegex = " *#*\\** *(" + String.join("|", summaryHeaders) + ") *#*\\** *:? *#*\\** *";
//		if (stats.explanation.matches("(?i)^" + summaryHeaderRegex + ".*$")) {
//			stats.explanation = stats.explanation.replaceFirst("(?i)" + summaryHeaderRegex, "");
//		}
	}
	
	private void processStat(String line) {
		Pair<TrackedIssue, Integer> stat = IssueStats.parseStat(line);
		  
		if (stat != null && stat.getRight() != IssueStats.NA)
		{
			interp.getIssueStats().setStat(stat.getLeft(), stat.getRight());
		}
	}
	
	private void processTitle(String line) {
		interp.setGenBillTitle(line);
	}
	
	private void processRider(String line) {
		if (line.matches("^ ?- ?.+$")) {
			line = line.replaceFirst(" ?- ?", "");
		} else if (line.matches("^ ?\\d\\.? ?.+$")) {
			line = line.replaceFirst(" ?\\d\\.? ?", "");
		}
		
		if (line.strip().toLowerCase().equals("none")) return;
		
		interp.getRiders().add(line);
	}
	
	private void processLongForm(String line) {
		interp.setLongExplain(interp.getLongExplain() + "\n" + line);
	}
	
	private void processShortForm(String line) {
		interp.setShortExplain(interp.getShortExplain() + "\n" + line);
	}
	
	private boolean setState(String line) {
	    for (State s : State.values()) {
	        for (String regex : s.regex) {
	            if (line.matches(regex + ".*")) {
	                state = s;

	                // Handle inline content (e.g., "Title: This is a title")
	                String inlineContent = line.replaceFirst(regex, "").strip();
	                if (!inlineContent.isEmpty()) {
	                    processContent(inlineContent);
	                }

	                return true;
	            }
	        }
	    }

	    return false;
	}
	
}
