package us.poliscore.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.LegislatorInterpretation;
import us.poliscore.model.VoteStatus;
import us.poliscore.service.storage.MemoryPersistenceService;
import us.poliscore.service.storage.S3PersistenceService;

@ApplicationScoped
public class LegislatorInterpretationService
{
	// Only process the x most recent bills
	public static final int LIMIT_BILLS = 4;
	
	public static final String PROMPT_TEMPLATE = "The provided text is a summary of the last {{time_period}} of legislative history of United States Legislator {{full_name}}. Please generate a concise (single paragraph) critique of this history, evaluating the performance, highlighting any specific accomplishments or alarming behaviour and pointing out major focuses and priorities of the legislator. In your critique, please attempt to reference concrete, notable and specific text of the summarized bills where possible.";
	
	@Inject
	private S3PersistenceService s3;
	
	@Inject
	private MemoryPersistenceService pService;
	
	@Inject
	private BillService billService;
	
	@Inject
	private BillInterpretationService billInterpreter;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private OpenAIService ai;
	
	public LegislatorInterpretation getOrCreate(String legislatorId)
	{
//		val cached = s3.retrieve(legislatorId.replaceFirst(Legislator.ID_CLASS_PREFIX, LegislatorInterpretation.ID_CLASS_PREFIX), LegislatorInterpretation.class);
//		
//		if (cached.isPresent())
//		{
//			return cached.get();
//		}
//		else
//		{
			val leg = legService.getById(legislatorId).orElseThrow();
			
			val interp = interpret(leg);
			
			return interp;
//		}
	}
	
	private int sortPriority(LegislatorBillInteraction interact) {
		if (interact instanceof LegislatorBillSponsor) return 3;
		else if (interact instanceof LegislatorBillCosponsor) return 2;
		else return 1;
	}
	
	protected List<LegislatorBillInteraction> getInteractionsForInterpretation(Legislator leg)
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
	
	protected void populateInteractionStats(Legislator leg)
	{
		for (val i : getInteractionsForInterpretation(leg))
		{
			val interp = s3.retrieve(BillInterpretation.generateId(i.getBillId(), null), BillInterpretation.class);
			
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
		List<String> aiUserMsg = new ArrayList<String>();
		
		for (val interact : leg.getInteractions())
		{
			if (interact.getIssueStats() != null)
			{
				if (interact instanceof LegislatorBillVote
						&& (((LegislatorBillVote)interact).getVoteStatus().equals(VoteStatus.NOT_VOTING) || ((LegislatorBillVote)interact).getVoteStatus().equals(VoteStatus.PRESENT)))
					continue;
				
				val weightedStats = interact.getIssueStats().multiply(interact.getJudgementWeight());
				stats = stats.sum(weightedStats, interact.getJudgementWeight());
				
				aiUserMsg.add(interact.describe() + ": " + interact.getIssueStats().getExplanation());
				
				periodStart = (periodStart == null) ? interact.getDate() : (periodStart.isAfter(interact.getDate()) ? interact.getDate() : periodStart);
			}
		}
		
		stats = stats.divideByTotalSummed();
		
		val prompt = PROMPT_TEMPLATE
				.replace("{{full_name}}", leg.getName().getOfficial_full())
				.replace("{{time_period}}", describeTimePeriod(periodStart, periodEnd));
		
		System.out.println(prompt);
		System.out.println(String.join("\n", aiUserMsg));
		val interpText = ai.chat(prompt, String.join("\n", aiUserMsg));
		stats.setExplanation(interpText);
		
		val interp = new LegislatorInterpretation(OpenAIService.metadata(), leg, stats);
		archive(interp);
		
		leg.setInterpretation(interp);
		
		legService.persist(leg);
		
		return interp;
	}
	
	protected String describeTimePeriod(LocalDate periodStart, LocalDate periodEnd)
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
	
	protected void archive(LegislatorInterpretation interp)
	{
		s3.store(interp);
		pService.store(interp);
	}
}
