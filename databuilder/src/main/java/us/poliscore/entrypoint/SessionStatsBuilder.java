package us.poliscore.entrypoint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.Environment;
import us.poliscore.PoliscoreUtil;
import us.poliscore.ai.BatchOpenAIRequest;
import us.poliscore.ai.BatchOpenAIRequest.BatchBillMessage;
import us.poliscore.ai.BatchOpenAIRequest.BatchOpenAIBody;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.DoubleIssueStats;
import us.poliscore.model.Party;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.LegislatorInterpretation;
import us.poliscore.model.session.SessionInterpretation;
import us.poliscore.model.session.SessionInterpretation.PartyBillInteraction;
import us.poliscore.model.session.SessionInterpretation.PartyInterpretation;
import us.poliscore.parsing.XMLBillSlicer;
import us.poliscore.service.BillInterpretationService;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.OpenAIService;
import us.poliscore.service.PartyInterpretationService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryPersistenceService;

@QuarkusMain(name="SessionStatsBuilder")
public class SessionStatsBuilder implements QuarkusApplication
{
	@Inject
	private MemoryPersistenceService memService;
	
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
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	public static void main(String[] args) {
		Quarkus.run(SessionStatsBuilder.class, args);
	}
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		billService.importUscBills();
		rollCallService.importUscVotes();
		
		
		// Initialize datastructures //
		val sessionStats = new SessionInterpretation();
		sessionStats.setSession(PoliscoreUtil.SUPPORTED_CONGRESSES.get(0));
		
		val doublePartyStats = new HashMap<Party, DoubleIssueStats>();
		val worstBills = new HashMap<Party, PriorityQueue<PartyBillInteraction>>();
		val bestBills = new HashMap<Party, PriorityQueue<PartyBillInteraction>>();
		val worstLegislators = new HashMap<Party, PriorityQueue<Legislator>>();
		val bestLegislators = new HashMap<Party, PriorityQueue<Legislator>>();
		for(val party : Party.values()) {
			doublePartyStats.put(party, new DoubleIssueStats());
			
			val p = party;
			worstBills.put(party, new PriorityQueue<>((a,b) -> (int) Math.round(a.getWeight() - b.getWeight())));
			bestBills.put(party, new PriorityQueue<>((a,b) -> (int) Math.round(b.getWeight() - a.getWeight())));
			bestLegislators.put(party, new PriorityQueue<>((a,b) -> (int) Math.round(b.getRating() - a.getRating())));
			worstLegislators.put(party, new PriorityQueue<>((a,b) -> (int) Math.round(a.getRating() - b.getRating())));
		}
		
		
		// Calculate Stats //
		for (val b : memService.query(Bill.class)) {
			val op = s3.get(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class);
			
			if (op.isPresent()) {
				val interp = op.get();
				b.setInterpretation(interp);
				
				val sponsor = memService.get(b.getSponsor().getId(), Legislator.class).orElseThrow();
				val party = sponsor.getTerms().last().getParty();
				val partyCosponsors = b.getCosponsors().stream().filter(sp -> memService.get(sp.getId(), Legislator.class).get().getParty().equals(party)).toList();
						
				val pbi = new PartyBillInteraction(b.getId(), b.getName(), b.getType(), b.getIntroducedDate(), b.getSponsor(), partyCosponsors, interp.getIssueStats().getRating(), interp.getShortExplain().substring(0, 300));
				bestBills.get(party).add(pbi);
				worstBills.get(party).add(pbi);
			}
		}
		for (val l : memService.query(Legislator.class).stream().filter(leg -> leg.isMemberOfSession(CongressionalSession.S118)).toList()) {
			val op = s3.get(LegislatorInterpretation.generateId(l.getId()), LegislatorInterpretation.class);
			
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
				if (!bestBills.get(party).isEmpty()) ps.getBestBills().add(bestBills.get(party).poll());
				if (!worstBills.get(party).isEmpty()) ps.getWorstBills().add(worstBills.get(party).poll());
				if (!bestLegislators.get(party).isEmpty()) ps.getBestLegislators().add(bestLegislators.get(party).poll());
				if (!worstLegislators.get(party).isEmpty()) ps.getWorstLegislators().add(worstLegislators.get(party).poll());
			}

			partyStats.put(party, ps);
		}
		
		
		// Use AI to generate explanations
		createRequest(partyStats.get(Party.DEMOCRAT), bestBills, worstBills);
		createRequest(partyStats.get(Party.REPUBLICAN), bestBills, worstBills);
		createRequest(partyStats.get(Party.INDEPENDENT), bestBills, worstBills);
		writeRequests(requests);
		
		sessionStats.setDemocrat(partyStats.get(Party.DEMOCRAT));
		sessionStats.setRepublican(partyStats.get(Party.REPUBLICAN));
		sessionStats.setIndependent(partyStats.get(Party.INDEPENDENT));
		
		sessionStats.setMetadata(OpenAIService.metadata());
		
		ddb.put(sessionStats);
		
		Log.info("Session stats build complete.");
	}
	
	private void createRequest(PartyInterpretation interp, HashMap<Party, PriorityQueue<PartyBillInteraction>> bestBills, HashMap<Party, PriorityQueue<PartyBillInteraction>> worstBills)
	{
		List<String> msg = new ArrayList<String>();
		val best = bestBills.get(interp.getParty());
		val worst = worstBills.get(interp.getParty());
		
		val grade = interp.getStats().getLetterGrade();
		if (grade.equals("A") || grade.equals("B")) {
			msg.add(StringUtils.join(interp.getBestBills().stream().map(i -> i.getShortExplainForInterp()).toArray(), "\n"));
			
			while (!best.isEmpty() && StringUtils.join(msg, "\n").length() + best.peek().getShortExplainForInterp().length() < XMLBillSlicer.MAX_SECTION_LENGTH) {
				msg.add(best.poll().getShortExplainForInterp());
			}
		} else if (grade.equals("C") || grade.equals("D")) {
			msg.add(StringUtils.join(interp.getBestBills().stream().map(i -> i.getShortExplainForInterp()).toArray(), "\n"));
			msg.add(StringUtils.join(interp.getWorstBills().stream().map(i -> i.getShortExplainForInterp()).toArray(), "\n"));
			
			while (!best.isEmpty() && StringUtils.join(msg, "\n").length() + best.peek().getShortExplainForInterp().length() < XMLBillSlicer.MAX_SECTION_LENGTH) {
				while (!worst.isEmpty() && StringUtils.join(msg, "\n").length() + worst.peek().getShortExplainForInterp().length() < XMLBillSlicer.MAX_SECTION_LENGTH) {
					msg.add(worst.poll().getShortExplainForInterp());
				}
			}
		} else {
			msg.add(StringUtils.join(interp.getWorstBills().stream().map(i -> i.getShortExplainForInterp()).toArray(), "\n"));
			
			while (!worst.isEmpty() && StringUtils.join(msg, "\n").length() + worst.peek().getShortExplainForInterp().length() < XMLBillSlicer.MAX_SECTION_LENGTH) {
				msg.add(worst.poll().getShortExplainForInterp());
			}
		}
		
		createRequest(interp.getParty(), PartyInterpretationService.getAiPrompt(PoliscoreUtil.SESSION, interp.getParty(), interp.getStats()), StringUtils.join(msg, "\n"));
	}
	
	private void createRequest(Party party, String sysMsg, String userMsg) {
		if (userMsg.length() >= XMLBillSlicer.MAX_SECTION_LENGTH) {
			throw new RuntimeException("Max user message length exceeded on " + party.getName() + " (" + userMsg.length() + " > " + XMLBillSlicer.MAX_SECTION_LENGTH);
		}
		
		List<BatchBillMessage> messages = new ArrayList<BatchBillMessage>();
		messages.add(new BatchBillMessage("system", sysMsg));
		messages.add(new BatchBillMessage("user", userMsg));
		
		requests.add(new BatchOpenAIRequest(
				SessionInterpretation.ID_CLASS_PREFIX + "/" + party.name(),
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
		
		System.out.println("Successfully wrote " + requests.size() + " requests to " + f.getAbsolutePath());
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
