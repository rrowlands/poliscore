package us.poliscore.entrypoint;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.model.DoubleIssueStats;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.service.BillInterpretationService;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryPersistenceService;

/**
 * This bulk importer is designed to import a full dataset built with the github.com/unitedstates/congress toolkit 
 */
@QuarkusMain(name="PartyStatsBuilder")
public class PartyStatsBuilder implements QuarkusApplication
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
		Quarkus.run(PartyStatsBuilder.class, args);
	}
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		billService.importUscBills();
		rollCallService.importUscVotes();
		
		var demStats = new DoubleIssueStats();
		var repubStats = new DoubleIssueStats();
		
		for (val b : memService.query(Bill.class)) {
			val op = ddb.get(b.getId(), Bill.class);
			if (op.isPresent()) {
				val interp = op.get().getInterpretation();
				val sponsor = memService.get(b.getSponsor().getId(), Legislator.class).orElseThrow();
				
				if (interp != null && "Democrat".equals(sponsor.getTerms().last().getParty())) {
					demStats = demStats.sum(interp.getIssueStats().toDoubleIssueStats());
				} else if (interp != null && "Republican".equals(sponsor.getTerms().last().getParty())) {
					repubStats = repubStats.sum(interp.getIssueStats().toDoubleIssueStats());
				}
			}
		}
		
		Log.info("Summed " + demStats.getTotalSummed().get(TrackedIssue.OverallBenefitToSociety));
		
		demStats = demStats.divideByTotalSummed();
		repubStats = repubStats.divideByTotalSummed();
		
		Log.info("Dem stats:");
		System.out.println(demStats);
		Log.info("Repub stats:");
		System.out.println(repubStats);
		
		Log.info("Poliscore database build complete.");
	}

	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
