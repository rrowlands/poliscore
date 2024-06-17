package ch.poliscore.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import ch.poliscore.VoteStatus;
import ch.poliscore.interpretation.OpenAIInterpretationMetadata;
import ch.poliscore.model.IssueStats;
import ch.poliscore.model.Legislator;
import ch.poliscore.model.LegislatorBillInteration;
import ch.poliscore.model.LegislatorBillInteration.LegislatorBillVote;
import ch.poliscore.model.LegislatorInterpretation;
import ch.poliscore.service.storage.ApplicationDataStoreIF;
import ch.poliscore.service.storage.S3PersistenceService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.val;

/**
 * This bill interpreter interprets the last x bills for a given politician.
 */
//@QuarkusMain(name="LegislatorBillInterpreter")
@ApplicationScoped
public class LegislatorInterpretationService
{
	// Only process the x most recent bills
	public static final int LIMIT_BILLS = 6;
	
	public static final String PROMPT_TEMPLATE = "The provided text is a summary of the last {{time_period}} of legislative history of United States Legislator {{full_name}}. Please generate a concise (single paragraph) summarization of this history, highlighting the accomplishments pointing out major focuses and priorities of the legislator. In your summary, please attempt to reference concrete, notable and specific text of the summarized bills where possible.";
	
	@Inject
	private S3PersistenceService s3;
	
	@Inject
	private ApplicationDataStoreIF pService;
	
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
		val cached = s3.retrieve(legislatorId, LegislatorInterpretation.class);
		
		if (cached.isPresent())
		{
			return cached.get();
		}
		else
		{
			val leg = legService.getById(legislatorId).orElseThrow();
			
			val interp = interpret(leg);
			
			return interp;
		}
	}
	
	protected LegislatorInterpretation interpret(Legislator leg)
	{
		// Make sure all their bills are interpreted
		int interpretedBills = 0;
		for (val interact : leg.getInteractions().stream().sorted(Comparator.comparing(LegislatorBillInteration::getDate).reversed()).collect(Collectors.toList()))
		{
			if (interpretedBills > LIMIT_BILLS) break;
			
			try
			{
				val interp = billInterpreter.getOrCreate(interact.getBillId());
				
				interact.setIssueStats(interp.getIssueStats());
				
				interpretedBills++;
			}
			catch (NoSuchElementException ex)
			{
				// TODO
				Log.error("Could not find text for bill " + interact.getBillId());
			}
		}
		
		IssueStats stats = new IssueStats();
		
		double weightSum = 0;
		long periodStart = -1;
		long periodEnd = new Date().getTime();
		List<String> aiUserMsg = new ArrayList<String>();
		
		for (val interact : leg.getInteractions())
		{
			if (interact.getIssueStats() != null)
			{
				if (interact instanceof LegislatorBillVote
						&& (((LegislatorBillVote)interact).getVoteStatus().equals(VoteStatus.NOT_VOTING) || ((LegislatorBillVote)interact).getVoteStatus().equals(VoteStatus.PRESENT)))
					continue;
				
				val weightedStats = interact.getIssueStats().multiply(interact.getJudgementWeight());
				stats = stats.sum(weightedStats);
				weightSum += interact.getJudgementWeight();
				
				aiUserMsg.add(interact.describe() + ": " + interact.getIssueStats().getExplanation());
				
				periodStart = (periodStart == -1) ? interact.getDate().getTime() : Math.min(periodStart, interact.getDate().getTime());
//				periodEnd = (periodEnd == -1) ? interact.getDate().getTime() : Math.max(periodEnd, interact.getDate().getTime());
			}
		}
		
		if (weightSum != 0)
			stats = stats.divide(weightSum);
		
		val prompt = PROMPT_TEMPLATE
				.replace("{{full_name}}", leg.getName().getOfficial_full())
				.replace("{{time_period}}", describeTimePeriod(periodStart, periodEnd));
		
		System.out.println(prompt);
		System.out.println(String.join("\n", aiUserMsg));
		val interpText = ai.chat(prompt, String.join("\n", aiUserMsg));
		stats.setExplanation(interpText);
		
		val interp = new LegislatorInterpretation(OpenAIInterpretationMetadata.construct(), leg, stats);
		archive(interp);
		
		leg.setInterpretation(interp);
		
		legService.persist(leg);
		
		return interp;
	}
	
	protected String describeTimePeriod(long periodStart, long periodEnd)
	{
		if (periodStart == -1 || periodEnd == -1) return "several months";
		
		long diffInMillies = periodEnd - periodStart;
	    long dayDiff = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
	    
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
