package us.poliscore.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.HashCodeBuilder;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.MissingBillTextException;
import us.poliscore.model.IssueStats;
import us.poliscore.model.Legislator;
import us.poliscore.model.LegislatorBillInteraction;
import us.poliscore.model.LegislatorBillInteraction.LegislatorBillCosponsor;
import us.poliscore.model.LegislatorBillInteraction.LegislatorBillSponsor;
import us.poliscore.model.LegislatorBillInteraction.LegislatorBillVote;
import us.poliscore.model.LegislatorInterpretation;
import us.poliscore.model.VoteStatus;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.parsing.BillSlicer;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.MemoryPersistenceService;

@ApplicationScoped
public class LegislatorInterpretationService
{
	// Ensure that the x most recent bills are interpreted
	public static final int LIMIT_BILLS = 999999;
	
	private static final String PROMPT_TEMPLATE = "The provided text is a summary of the last {{time_period}} of legislative history of United States Legislator {{full_name}}. Please generate a concise (single paragraph) critique of this history, evaluating the performance, highlighting any specific accomplishments or alarming behaviour and pointing out major focuses and priorities of the legislator. In your critique, please attempt to reference concrete, notable and specific text of the summarized bills where possible.";
	
	@Inject
	private LocalCachedS3Service s3;
	
	@Inject
	private BillService billService;
	
	@Inject
	private BillInterpretationService billInterpreter;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private MemoryPersistenceService memService;
	
	@Inject
	private OpenAIService ai;
	
	public LegislatorInterpretation getOrCreate(String legislatorId)
	{
		val cached = s3.get(legislatorId.replaceFirst(Legislator.ID_CLASS_PREFIX, LegislatorInterpretation.ID_CLASS_PREFIX), LegislatorInterpretation.class);
		
		val leg = legService.getById(legislatorId).orElseThrow();
		populateInteractionStats(leg);
		
		if (cached.isPresent()) //  && calculateInterpHashCode(leg) == cached.get().getHash()
		{
			return cached.get();
		}
		else
		{
			val interp = interpret(leg);
			
			return interp;
		}
	}
	
	public int calculateInterpHashCode(Legislator leg)
	{
		val builder = new HashCodeBuilder();
		
		for (val interact : getInteractionsForInterpretation(leg).stream().filter(i -> i.getIssueStats() != null && !(i instanceof LegislatorBillVote
						&& (((LegislatorBillVote)i).getVoteStatus().equals(VoteStatus.NOT_VOTING) || ((LegislatorBillVote)i).getVoteStatus().equals(VoteStatus.PRESENT)))).toList()) {
			builder.append(interact.getBillId());
		}
		
		return builder.build();
	}
	
	protected int sortPriority(LegislatorBillInteraction interact) {
		if (interact instanceof LegislatorBillSponsor) return 3;
		else if (interact instanceof LegislatorBillCosponsor) return 2;
		else return 1;
	}
	
	public List<LegislatorBillInteraction> getInteractionsForInterpretation(Legislator leg)
	{
		return leg.getInteractions().stream()
				// Remove duplicate bill interactions, favoring sponsor and co-sponsor over vote
				.collect(Collectors.groupingBy(LegislatorBillInteraction::getBillId,Collectors.toList())).values().stream()
					.map(l -> l.size() > 1 ? l.stream().sorted((aa,bb) -> sortPriority(bb) - sortPriority(aa)).findFirst().get() : l.get(0))
				.filter(i -> isRelevant(i))
				.sorted(Comparator.comparing(LegislatorBillInteraction::getDate).reversed())
				.collect(Collectors.toList());
	}
	
	protected void interpretMostRecentInteractions(Legislator leg)
	{
		int interpretedBills = 0;
		for (val i : getInteractionsForInterpretation(leg))
		{
			if (interpretedBills >= LIMIT_BILLS) break;
			
			try
			{
				val interp = billInterpreter.getOrCreate(i.getBillId());
				
				i.setIssueStats(interp.getIssueStats());
				
				interpretedBills++;
			}
			catch (MissingBillTextException ex)
			{
				// TODO
				Log.error("Could not find text for bill " + i.getBillId());
			}
		}
	}
	
	protected boolean isRelevant(LegislatorBillInteraction interact)
	{
		return !(interact instanceof LegislatorBillVote
		&& (((LegislatorBillVote)interact).getVoteStatus().equals(VoteStatus.NOT_VOTING) || ((LegislatorBillVote)interact).getVoteStatus().equals(VoteStatus.PRESENT)));
	}
	
	public void populateInteractionStats(Legislator leg)
	{
		for (val i : getInteractionsForInterpretation(leg))
		{
			val interp = s3.get(BillInterpretation.generateId(i.getBillId(), null), BillInterpretation.class);
			
			if (interp.isPresent()) {
				i.setIssueStats(interp.get().getIssueStats());
			}
		}
	}
	
	protected LegislatorInterpretation interpret(Legislator leg)
	{
		interpretMostRecentInteractions(leg);
		
		populateInteractionStats(leg);
		
		IssueStats stats = new IssueStats();
		
		LocalDate periodStart = null;
		val periodEnd = LocalDate.now();
		List<String> billMsgs = new ArrayList<String>();
		
		for (val interact : getInteractionsForInterpretation(leg))
		{
			if (interact.getIssueStats() != null)
			{
				if (interact instanceof LegislatorBillVote
						&& (((LegislatorBillVote)interact).getVoteStatus().equals(VoteStatus.NOT_VOTING) || ((LegislatorBillVote)interact).getVoteStatus().equals(VoteStatus.PRESENT)))
					continue;
				
				val weightedStats = interact.getIssueStats().multiply(interact.getJudgementWeight());
				stats = stats.sum(weightedStats, Math.abs(interact.getJudgementWeight()));
				
				val billMsg = interact.describe() + ": " + interact.getIssueStats().getExplanation();
				if ( (String.join("\n", billMsgs) + "\n" + billMsg).length() < BillSlicer.MAX_SECTION_LENGTH ) {
					billMsgs.add(billMsg);
					periodStart = (periodStart == null) ? interact.getDate() : (periodStart.isAfter(interact.getDate()) ? interact.getDate() : periodStart);
				}
			}
		}
		
		stats = stats.divideByTotalSummed();
		
		val prompt = getAiPrompt(leg, periodStart, periodEnd);
		System.out.println(prompt);
		System.out.println(String.join("\n", billMsgs));
		val interpText = ai.chat(prompt, String.join("\n", billMsgs));
		stats.setExplanation(interpText);
		
		val interp = new LegislatorInterpretation(OpenAIService.metadata(), leg, stats);
		interp.setHash(calculateInterpHashCode(leg));
		s3.put(interp);
		
		leg.setInterpretation(interp);
		
		memService.put(leg);
		
		return interp;
	}
	
	public static String getAiPrompt(Legislator leg, LocalDate periodStart, LocalDate periodEnd) {
		return PROMPT_TEMPLATE
		.replace("{{full_name}}", leg.getName().getOfficial_full())
		.replace("{{time_period}}", describeTimePeriod(periodStart, periodEnd));
	}
	
	public static String describeTimePeriod(LocalDate periodStart, LocalDate periodEnd)
	{
		if (periodStart == null || periodEnd == null) return "several months";
		
		long dayDiff = ChronoUnit.DAYS.between(periodStart, periodEnd);
	    
	    if (dayDiff < 30)
	    {
	    	return dayDiff + " days";
	    }
	    else if (dayDiff < (30 * 11))
	    {
	    	int months = Math.round((float)dayDiff / 30f);
	    	
	    	return months + " month" + (months <= 1 ? "" : "s");
	    }
	    else
	    {
	    	int years = Math.round((float)dayDiff / 365f);
	    	
	    	return years + " year" + (years <= 1 ? "" : "s");
	    }
	}
}
