package us.poliscore.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.Environment;
import us.poliscore.PoliscoreUtil;
import us.poliscore.ai.BatchOpenAIRequest;
import us.poliscore.ai.BatchOpenAIRequest.BatchBillMessage;
import us.poliscore.ai.BatchOpenAIRequest.BatchOpenAIBody;
import us.poliscore.ai.BatchOpenAIRequest.CustomData;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.DoubleIssueStats;
import us.poliscore.model.IssueStats;
import us.poliscore.model.Party;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.LegislatorInterpretation;
import us.poliscore.model.session.SessionInterpretation;
import us.poliscore.model.session.SessionInterpretation.PartyBillInteraction;
import us.poliscore.model.session.SessionInterpretation.PartyInterpretation;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryObjectService;

@ApplicationScoped
public class PartyInterpretationService {
	public static final String PROMPT_TEMPLATE = """
			You are part of a U.S. non-partisan oversight committee which has graded the {{partyName}} party for the {{session}} congressional session. The evaluations were compiled based on grading every bill within the session, and then aggregating the scores. The party has received the following policy area grades (scores range from -100 to 100):
			
			{{stats}}
			
			Based on these scores, this party has received the overall letter grade: {{letterGrade}}. You will be given summaries of bills this party has introduced within this congressional session, sorted by two different scoring and sorting mechanisms: rating and impact. Rating was calculated by directly sorting the bills based on the \"Overall Benefit to Society\" metric. Impact is a metric which factors in rating, number of cosponsors, and how far the bill made it through the legislative process (i.e. laws are more important than bills). Highest and lowest rated bills can be useful for knowing what the extremes of the party are up to, versus impact is useful for knowing what the party actually found coalitions around. Please generate a layman's, concise, five paragraph, {{analysisType}}. Begin your first paragraph by focusing on higher level goals, highlighting any {{behavior}}, identifying trends, and pointing out major focuses and priorities of the party. Your next three paragraphs will attempt to explain why the party received the scores they did in the following policy areas: [{{highlightPolicyAreas}}] and should reference specific bill titles (in quotes). Do not include the party's policy area grade scores and do not mention their letter grade in your summary.
			""";
	
	@Inject
	private MemoryObjectService memService;
	
	@Inject
	private LocalFilePersistenceService localStore;
	
	@Inject
	private DynamoDbPersistenceService ddb;
	
	@Inject
	private LocalCachedS3Service s3;
	
	@Inject
	private BillService billService;
	
	@Inject
	private BillInterpretationService billInterpreter;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private RollCallService rollCallService;
	
	@Inject
	private LegislatorInterpretationService legInterp;
	
	private List<BatchOpenAIRequest> requests = new ArrayList<BatchOpenAIRequest>();
	
	private List<File> writtenFiles = new ArrayList<File>();
	
	private HashMap<Party, Map<TrackedIssue, PriorityQueue<PartyBillInteraction>>> bestBillsByIssue;
	
	private HashMap<Party, Map<TrackedIssue, PriorityQueue<PartyBillInteraction>>> worstBillsByIssue;
	
	public List<File> process() throws IOException
	{
		legService.importLegislators();
		billService.importUscBills();
		rollCallService.importUscVotes();
		
		val partyStats = recalculateStats();
		
		// Use AI to generate explanations
		createRequest(partyStats.getDemocrat());
		createRequest(partyStats.getRepublican());
		createRequest(partyStats.getIndependent());
		writeRequests(requests);
		
		return writtenFiles;
	}
	
	public SessionInterpretation recalculateStats()
	{
		// Initialize datastructures //
		val sessionStats = new SessionInterpretation();
		sessionStats.setSession(PoliscoreUtil.CURRENT_SESSION.getNumber());
		
		val mostImpactfulBills = new HashMap<Party, PriorityQueue<PartyBillInteraction>>();
		val leastImpactfulBills = new HashMap<Party, PriorityQueue<PartyBillInteraction>>();
		val worstBills = new HashMap<Party, PriorityQueue<PartyBillInteraction>>();
		val bestBills = new HashMap<Party, PriorityQueue<PartyBillInteraction>>();
		bestBillsByIssue = new HashMap<Party, Map<TrackedIssue, PriorityQueue<PartyBillInteraction>>>();
		worstBillsByIssue = new HashMap<Party, Map<TrackedIssue, PriorityQueue<PartyBillInteraction>>>();
		
		val doublePartyStats = new HashMap<Party, DoubleIssueStats>();
		val worstLegislators = new HashMap<Party, PriorityQueue<Legislator>>();
		val bestLegislators = new HashMap<Party, PriorityQueue<Legislator>>();
		for(val party : Party.values()) {
			doublePartyStats.put(party, new DoubleIssueStats());
			
			leastImpactfulBills.put(party, new PriorityQueue<>(Comparator.comparing(PartyBillInteraction::getImpact)));
			mostImpactfulBills.put(party, new PriorityQueue<>(Comparator.comparing(PartyBillInteraction::getImpact).reversed()));
			worstBills.put(party, new PriorityQueue<>(Comparator.comparing(PartyBillInteraction::getRating)));
			bestBills.put(party, new PriorityQueue<>(Comparator.comparing(PartyBillInteraction::getRating).reversed()));
			bestLegislators.put(party, new PriorityQueue<>((a,b) -> (int) (b.getImpact() - a.getImpact())));
			worstLegislators.put(party, new PriorityQueue<>(Comparator.comparing(Legislator::getImpact)));
			
			val bestPartyBillsByIssue = new HashMap<TrackedIssue, PriorityQueue<PartyBillInteraction>>();
			bestBillsByIssue.put(party, bestPartyBillsByIssue);
			val worstPartyBillsByIssue = new HashMap<TrackedIssue, PriorityQueue<PartyBillInteraction>>();
			worstBillsByIssue.put(party, worstPartyBillsByIssue);
			for(val issue : TrackedIssue.values())
			{
				bestPartyBillsByIssue.put(issue, new PriorityQueue<PartyBillInteraction>(Comparator.comparing(PartyBillInteraction::getRating).reversed()));
				worstPartyBillsByIssue.put(issue, new PriorityQueue<PartyBillInteraction>(Comparator.comparing(PartyBillInteraction::getRating)));
			}
		}
		
		// Calculate Stats //
		for (val b : memService.query(Bill.class)) {
			val op = s3.get(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class);
			
			if (op.isPresent()) {
				val interp = op.get();
				b.setInterpretation(interp);
				
				val sponsor = memService.get(b.getSponsor().getId(), Legislator.class).orElseThrow();
				val party = sponsor.getTerms().last().getParty();
				val partyCosponsors = b.getCosponsors().stream().filter(sp -> memService.exists(sp.getId(), Legislator.class) && memService.get(sp.getId(), Legislator.class).get().getParty().equals(party)).toList();
						
				val pbi = new PartyBillInteraction(b.getId(), b.getName(), b.getStatus(), b.getType(), b.getIntroducedDate(), b.getSponsor(), partyCosponsors, b.getRating(), b.getImpact(), interp.getShortExplain());
				mostImpactfulBills.get(party).add(pbi);
				leastImpactfulBills.get(party).add(pbi);
				bestBills.get(party).add(pbi);
				worstBills.get(party).add(pbi);
				
				for(val issue : TrackedIssue.values())
				{
					var issuePbi = new PartyBillInteraction(b.getId(), b.getName(), b.getStatus(), b.getType(), b.getIntroducedDate(), b.getSponsor(), partyCosponsors, interp.getIssueStats().getStat(issue), b.getImpact(issue), interp.getShortExplain());
					bestBillsByIssue.get(party).get(issue).offer(issuePbi);
					worstBillsByIssue.get(party).get(issue).offer(issuePbi);
				}
			}
		}
		for (val l : memService.query(Legislator.class).stream().filter(leg -> leg.isMemberOfSession(CongressionalSession.S118)).toList()) {
			val op = s3.get(LegislatorInterpretation.generateId(l.getId(), sessionStats.getSession()), LegislatorInterpretation.class);
			
			if (op.isPresent()) {
				val interp = op.get();
				val party = l.getParty();
				
				l.getInteractions().clear();
				
				val t = l.getTerms().last();
				l.getTerms().clear();
				l.getTerms().add(t);
				
				l.setInterpretation(interp);
				
				bestLegislators.get(party).add(l);
				worstLegislators.get(party).add(l);
				
				val ps = doublePartyStats.get(party);
				doublePartyStats.put(party, ps.sum(interp.getIssueStats().toDoubleIssueStats()));
			}
		}
		
		// Build persistant data //
		val partyStats = new HashMap<Party, PartyInterpretation>();
		for(val party : Party.values()) {
			val ps = new PartyInterpretation();
			
			var stats = doublePartyStats.get(party);
			stats = stats.divideByTotalSummed();
			ps.setStats(stats.toIssueStats());
			
			ps.setParty(party);
			
			for (int i = 0; i < 20; ++i) {
				if (!mostImpactfulBills.get(party).isEmpty()) ps.getMostImportantBills().add(mostImpactfulBills.get(party).poll());
				if (!leastImpactfulBills.get(party).isEmpty()) ps.getLeastImportantBills().add(leastImpactfulBills.get(party).poll());
				if (!bestBills.get(party).isEmpty()) ps.getBestBills().add(bestBills.get(party).poll());
				if (!worstBills.get(party).isEmpty()) ps.getWorstBills().add(worstBills.get(party).poll());
				if (!bestLegislators.get(party).isEmpty()) ps.getBestLegislators().add(bestLegislators.get(party).poll());
				if (!worstLegislators.get(party).isEmpty()) ps.getWorstLegislators().add(worstLegislators.get(party).poll());
			}

			partyStats.put(party, ps);
		}
		
		sessionStats.setDemocrat(partyStats.get(Party.DEMOCRAT));
		sessionStats.setRepublican(partyStats.get(Party.REPUBLICAN));
		sessionStats.setIndependent(partyStats.get(Party.INDEPENDENT));
		
		sessionStats.setMetadata(OpenAIService.metadata());
		
//		val op = s3.get(sessionStats.getId(), SessionInterpretation.class);
//		if (op.isPresent()) {
//			sessionStats.getDemocrat().setLongExplain(op.get().getDemocrat().getLongExplain());
//			sessionStats.getRepublican().setLongExplain(op.get().getRepublican().getLongExplain());
//			sessionStats.getIndependent().setLongExplain(op.get().getIndependent().getLongExplain());
//		}
//		ddb.put(sessionStats);
		
		return sessionStats;
	}
	
	private void createRequest(PartyInterpretation interp)
	{
		List<String> msg = new ArrayList<String>();
		
		msg.add("Highest Impact Bills:");
		msg.add(StringUtils.join(interp.getMostImportantBills().stream().map(i -> i.getShortExplainForInterp()).toArray(), "\n"));
		
		msg.add("\n");
		
		val grade = interp.getStats().getLetterGrade();
		if (grade.equals("A") || grade.equals("B")) {
			msg.add("Highest \"Overall Benefit to Society\" (Rating) Bills:");
			msg.add(StringUtils.join(interp.getBestBills().stream().limit(5).map(i -> i.getShortExplainForInterp()).toArray(), "\n"));
		} else if (grade.equals("C") || grade.equals("D")) {
			msg.add("Highest \"Overall Benefit to Society\" (Rating) Bills:");
			msg.add(StringUtils.join(interp.getBestBills().stream().limit(5).map(i -> i.getShortExplainForInterp()).toArray(), "\n"));
			msg.add("Lowest \"Overall Benefit to Society\" (Rating) Bills:");
			msg.add(StringUtils.join(interp.getWorstBills().stream().limit(5).map(i -> i.getShortExplainForInterp()).toArray(), "\n"));
		} else {
			msg.add("Lowest \"Overall Benefit to Society\" (Rating) Bills:");
			msg.add(StringUtils.join(interp.getWorstBills().stream().limit(5).map(i -> i.getShortExplainForInterp()).toArray(), "\n"));
		}
		
		for(val issue : getHighlightPolicyAreas(interp.getStats()))
		{
			msg.add("\nLargest Contributors To \"" + issue.getName() + "\" Score:\n");
			
			if (interp.getStats().getStat(issue) >= 0)
				msg.add(StringUtils.join(queueTake(10, bestBillsByIssue.get(interp.getParty()).get(issue)).stream().map(i -> i.getShortExplainForInterp()).toArray(), "\n"));
			else
				msg.add(StringUtils.join(queueTake(10, worstBillsByIssue.get(interp.getParty()).get(issue)).stream().map(i -> i.getShortExplainForInterp()).toArray(), "\n"));
		}
		
		createRequest(interp.getParty(), PartyInterpretationService.getAiPrompt(PoliscoreUtil.CURRENT_SESSION, interp.getParty(), interp.getStats()), StringUtils.join(msg, "\n"));
	}
	
	private List<PartyBillInteraction> queueTake(int amt, PriorityQueue<PartyBillInteraction> queue)
	{
		List<PartyBillInteraction> result = new ArrayList<PartyBillInteraction>();
		
		while (!queue.isEmpty() && result.size() < 10)
		{
			result.add(queue.poll());
		}
		
		return result;
	}
	
	private void createRequest(Party party, String sysMsg, String userMsg) {
		if (userMsg.length() >= OpenAIService.MAX_REQUEST_LENGTH) {
			throw new RuntimeException("Max user message length exceeded on " + party.getName() + " (" + userMsg.length() + " > " + OpenAIService.MAX_REQUEST_LENGTH);
		}
		
		List<BatchBillMessage> messages = new ArrayList<BatchBillMessage>();
		messages.add(new BatchBillMessage("system", sysMsg));
		messages.add(new BatchBillMessage("user", userMsg));
		
		requests.add(new BatchOpenAIRequest(
				new CustomData(SessionInterpretation.ID_CLASS_PREFIX + "/" + party.name()),
				new BatchOpenAIBody(messages)
		));
	}
	
	private void writeRequests(List<BatchOpenAIRequest> requests) throws IOException {
		File f = new File(Environment.getDeployedPath(), "openai-party-requests.jsonl");
		
		val mapper = PoliscoreUtil.getObjectMapper();
		val s = requests.stream().map(r -> {
			try {
				return mapper.writeValueAsString(r);
			} catch (JsonProcessingException e) {
				throw new RuntimeException(e);
			}
		}).toList();
		
		FileUtils.write(f, String.join("\n", s), "UTF-8");
		
		writtenFiles.add(f);
		
		Log.info("Successfully wrote " + requests.size() + " requests to " + f.getAbsolutePath());
	}
	
	public static List<TrackedIssue> getHighlightPolicyAreas(IssueStats stats)
	{
		val grade = stats.getLetterGrade();
		List<TrackedIssue> highlightPolicyAreas;
		
		if (grade.equals("A") || grade.equals("B")) {
			highlightPolicyAreas = Arrays.asList(TrackedIssue.values()).stream()
				.filter(i -> !i.equals(TrackedIssue.OverallBenefitToSociety))
				.sorted((a,b) -> (int)Math.round(stats.getStat(b) - stats.getStat(a)))
				.limit(4)
				.collect(Collectors.toList());
		} else if (grade.equals("C") || grade.equals("D")) {
			highlightPolicyAreas = Arrays.asList(TrackedIssue.values()).stream()
				.filter(i -> !i.equals(TrackedIssue.OverallBenefitToSociety))
				.sorted((a,b) -> (int)Math.round(stats.getStat(b) - stats.getStat(a)))
				.limit(2)
				.collect(Collectors.toList());
			highlightPolicyAreas.addAll(Arrays.asList(TrackedIssue.values()).stream()
					.filter(i -> !i.equals(TrackedIssue.OverallBenefitToSociety))
					.sorted((a,b) -> (int)Math.round(stats.getStat(a) - stats.getStat(b)))
					.limit(2)
					.collect(Collectors.toList()));
		} else {
			highlightPolicyAreas = Arrays.asList(TrackedIssue.values()).stream()
					.filter(i -> !i.equals(TrackedIssue.OverallBenefitToSociety))
					.sorted((a,b) -> (int)Math.round(stats.getStat(a) - stats.getStat(b)))
					.limit(2)
					.collect(Collectors.toList());
		}
		
		return highlightPolicyAreas;
	}
	
	public static String getAiPrompt(CongressionalSession session, Party party, IssueStats stats) {
		val grade = stats.getLetterGrade();
		
		return PROMPT_TEMPLATE
				.replace("{{partyName}}", party.getName())
				.replace("{{session}}", String.valueOf(session.getNumber()))
				.replace("{{stats}}", stats.toString())
				.replace("{{letterGrade}}", grade)
				.replace("{{analysisType}}", grade.equals("A") || grade.equals("B") ? "endorsement" : (grade.equals("C") || grade.equals("D") ? "mixed analysis" : "harsh critique"))
				.replace("{{behavior}}", grade.equals("A") || grade.equals("B") ? "specific accomplishments" : (grade.equals("C") || grade.equals("D") ? "specific accomplishments or alarming behaviour" : "alarming behaviour"))
				.replace("{{highlightPolicyAreas}}", String.join(", ", getHighlightPolicyAreas(stats).stream().map(ti -> ti.getName()).toList()));
	}
}
