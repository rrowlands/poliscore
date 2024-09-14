package us.poliscore.entrypoint;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.DoubleIssueStats;
import us.poliscore.model.Party;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.stats.SessionStats;
import us.poliscore.model.stats.SessionStats.PartyBillInteraction;
import us.poliscore.model.stats.SessionStats.PartyBillSet;
import us.poliscore.model.stats.SessionStats.PartyLegislatorSet;
import us.poliscore.model.stats.SessionStats.PartyStats;
import us.poliscore.service.BillInterpretationService;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
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
		val sessionStats = new SessionStats();
		sessionStats.setSession(PoliscoreUtil.SUPPORTED_CONGRESSES.get(0));
		
		val doublePartyStats = new HashMap<Party, DoubleIssueStats>();
		val worstBills = new HashMap<Party, PriorityQueue<PartyBillInteraction>>();
		val bestBills = new HashMap<Party, PriorityQueue<PartyBillInteraction>>();
		val worstLegislators = new HashMap<Party, PriorityQueue<Legislator>>();
		val bestLegislators = new HashMap<Party, PriorityQueue<Legislator>>();
		for(val party : Party.values()) {
			doublePartyStats.put(party, new DoubleIssueStats());
			worstBills.put(party, new PriorityQueue<>((a,b) -> (int) Math.round(a.getWeight() - b.getWeight())));
			bestBills.put(party, new PriorityQueue<>((a,b) -> (int) Math.round(b.getWeight() - a.getWeight())));
			bestLegislators.put(party, new PriorityQueue<>((a,b) -> (int) Math.round(b.getRating() - a.getRating())));
			worstLegislators.put(party, new PriorityQueue<>((a,b) -> (int) Math.round(a.getRating() - b.getRating())));
		}
		
		
		// Calculate Stats //
		for (val b : memService.query(Bill.class)) {
			val op = ddb.get(b.getId(), Bill.class);
			if (op.isPresent()) {
				val interp = op.get().getInterpretation();
				val sponsor = memService.get(b.getSponsor().getId(), Legislator.class).orElseThrow();
				val party = sponsor.getTerms().last().getParty();
				
				val ps = doublePartyStats.get(party);
				doublePartyStats.put(party, ps.sum(interp.getIssueStats().toDoubleIssueStats()));
				
				val pbi = new PartyBillInteraction(b.getId(), b.getName(), b.getSponsor(), b.getCosponsors(), interp.getIssueStats().getRating());
				bestBills.get(party).add(pbi);
				worstBills.get(party).add(pbi);
			}
		}
		for (val l : memService.query(Legislator.class).stream().filter(leg -> leg.isMemberOfSession(CongressionalSession.S118)).toList()) {
			l.setInteractions(null);
			l.setTerms(null);
			bestLegislators.get(l.getParty()).add(l);
			worstLegislators.get(l.getParty()).add(l);
		}
		
		// Build persistant data //
		val partyStats = new HashMap<Party, PartyStats>();
		for(val party : Party.values()) {
			val ps = new PartyStats();
			
			var stats = doublePartyStats.get(party);
			stats = stats.divideByTotalSummed();
			ps.setStats(stats.toIssueStats());
			
			ps.setParty(party);
			ps.setBestBills(bestBills.get(party).stream().limit(10).collect(Collectors.toCollection(PartyBillSet::new)));
			ps.setWorstBills(worstBills.get(party).stream().limit(10).collect(Collectors.toCollection(PartyBillSet::new)));
			ps.setBestLegislators(bestLegislators.get(party).stream().limit(10).collect(Collectors.toCollection(PartyLegislatorSet::new)));
			ps.setWorstLegislators(worstLegislators.get(party).stream().limit(10).collect(Collectors.toCollection(PartyLegislatorSet::new)));
			
			partyStats.put(party, ps);
		}
		
		sessionStats.setPartyStats(partyStats);
		
		ddb.put(sessionStats);
		
		Log.info("Session stats build complete.");
	}

	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
