package us.poliscore.entrypoint;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.PoliscoreUtil;
import us.poliscore.entrypoint.batch.BatchBillRequestGenerator;
import us.poliscore.entrypoint.batch.BatchLegislatorRequestGenerator;
import us.poliscore.entrypoint.batch.BatchOpenAIResponseImporter;
import us.poliscore.model.DoubleIssueStats;
import us.poliscore.model.IssueStats;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.legislator.LegislatorInterpretation;
import us.poliscore.service.BillInterpretationService;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.OpenAIService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryPersistenceService;

/**
 * Run this to keep a deployed server up-to-date.
 */
@QuarkusMain(name="DatabaseBuilder")
public class DatabaseBuilder implements QuarkusApplication
{
	@Inject
	private S3ImageDatabaseBuilder imageBuilder;
	
	@Inject
	private GPOBulkBillTextFetcher billTextFetcher;
	
	@Inject
	private BatchBillRequestGenerator billRequestGenerator;
	
	@Inject
	private BatchLegislatorRequestGenerator legislatorRequestGenerator;
	
	@Inject
	private WebappDataGenerator webappDataGenerator;
	
	@Inject
	private BatchOpenAIResponseImporter responseImporter;
	
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
	protected OpenAIService openAi;
	
	@Inject
	private LegislatorInterpretationService legInterp;
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	public static void main(String[] args) {
		Quarkus.run(DatabaseBuilder.class, args);
	}
	
	protected void process() throws IOException
	{
		updateUscLegislators();
		
		legService.importLegislators();
		billService.importUscBills();
		rollCallService.importUscVotes();
		
		imageBuilder.process();
		billTextFetcher.process();
		
		interpretBills();
		interpretLegislators();
		// TODO : Party Stats
		
		webappDataGenerator.process();
		
		Log.info("Poliscore database build complete.");
	}
	
	private void updateUscLegislators()
	{
		// TODO
	}
	
	@SneakyThrows
	private void interpretBills() {
		List<File> requests = billRequestGenerator.process();
		
		if (requests.size() > 0) {
			List<File> responses = openAi.processBatch(requests);
			
			for (File f : responses) {
				responseImporter.process(f);
			}
			
			interpretBills();
		}
	}
	
	@SneakyThrows
	private void interpretLegislators() {
		if (Boolean.TRUE) {
			recalculateLegislators();
		} else {
			// TODO : We don't always want to interpret all the legislators.
			//        This code would ideally be run on a schedule, i.e. once a month or once every 3-6 months
			List<File> requests = legislatorRequestGenerator.process();
		
			if (requests.size() > 0) {
				List<File> responses = openAi.processBatch(requests);
				
				for (File f : responses) {
					responseImporter.process(f);
				}
			}
		}
	}
	
	/**
	 * Recalculates all legislator stats and bill interactions without actually re-interpreting their activity. Saves on AI interpretation costs while
	 * still allowing stats and interactions to remain up-to-date.
	 */
	private void recalculateLegislators() {
		for (val leg : memService.query(Legislator.class).stream()
				.filter(l -> l.isMemberOfSession(PoliscoreUtil.SESSION))
				.collect(Collectors.toList()))
		{
			for (val i : legInterp.getInteractionsForInterpretation(leg))
			{
				val interp = s3.get(BillInterpretation.generateId(i.getBillId(), null), BillInterpretation.class);
				
				if (interp.isPresent()) {
					i.setIssueStats(interp.get().getIssueStats());
					
					val bill = memService.get(i.getBillId(), Bill.class).orElseThrow();
					bill.setInterpretation(interp.get());
					i.setBillName(bill.getName());
				}
			}
			
			DoubleIssueStats doubleStats = new DoubleIssueStats();
			
			val interacts = new LegislatorBillInteractionSet();
			for (val interact : legInterp.getInteractionsForInterpretation(leg))
			{
				if (interact.getIssueStats() != null)
				{
					val weightedStats = interact.getIssueStats().multiply(interact.getJudgementWeight());
					doubleStats = doubleStats.sum(weightedStats, Math.abs(interact.getJudgementWeight()));
					
					interacts.add(interact);
				}
			}
			
			doubleStats = doubleStats.divideByTotalSummed();
			IssueStats stats = doubleStats.toIssueStats();
			
			val interp = ddb.get(LegislatorInterpretation.generateId(leg.getId()), LegislatorInterpretation.class).get();
			interp.setHash(legInterp.calculateInterpHashCode(leg));
			
			interp.setIssueStats(stats);
			
			if (interp.getIssueStats() == null || !interp.getIssueStats().hasStat(TrackedIssue.OverallBenefitToSociety) || StringUtils.isBlank(interp.getLongExplain())) {
				throw new RuntimeException("Unable to parse valid issue stats for legislator " + leg.getId());
			}
			
//			s3.put(interp);
			
			// 1100 seems to be about an upper limit for a single ddb page
			leg.setInteractions(interacts.stream().sorted((a,b) -> a.getDate().compareTo(b.getDate())).limit(1100).collect(Collectors.toCollection(LegislatorBillInteractionSet::new)));
	//		leg.setInteractions(interacts);
			
			leg.setInterpretation(interp);
			
			ddb.put(leg);
		}
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
