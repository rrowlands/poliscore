package us.poliscore.entrypoint;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.Environment;
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
import us.poliscore.service.PartyInterpretationService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryObjectService;

/**
 * Run this to keep a deployed server up-to-date.
 */
@QuarkusMain(name="DatabaseBuilder")
public class DatabaseBuilder implements QuarkusApplication
{
	public static boolean REINTERPRET_LEGISLATORS = false;
	
	public static boolean REINTERPRET_PARTIES = false;
	
	@Inject
	private S3ImageDatabaseBuilder imageBuilder;
	
	@Inject
	private GPOBulkBillTextFetcher billTextFetcher;
	
	@Inject
	private BatchBillRequestGenerator billRequestGenerator;
	
	@Inject
	private BatchLegislatorRequestGenerator legislatorRequestGenerator;
	
	@Inject
	private PartyInterpretationService partyInterpreter;
	
	@Inject
	private WebappDataGenerator webappDataGenerator;
	
	@Inject
	private BatchOpenAIResponseImporter responseImporter;
	
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
	protected OpenAIService openAi;
	
	@Inject
	private LegislatorInterpretationService legInterp;
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	protected void process() throws IOException
	{
		updateUscLegislators();
		
		s3.optimizeExists(BillInterpretation.class);
		s3.optimizeExists(LegislatorInterpretation.class);
		
		legService.importLegislators();
		billService.importUscBills();
		rollCallService.importUscVotes();
		
		imageBuilder.process();
		billTextFetcher.process();
		
		buildFromS3();
		interpretBills();
		interpretLegislators();
		interpretPartyStats();
		
		webappDataGenerator.process();
		
		Log.info("Poliscore database build complete.");
	}
	
	@SneakyThrows
	private void buildFromS3()
	{
		Log.info("Making sure that our ddb bill database is up-to-date with what exists on s3.");
		
		long amount = 0;
		
		// TODO : As predicted, this is crazy slow. We might need to create a way to 'optimizeExists' for ddb
		for (Bill b : memService.query(Bill.class).stream().filter(b -> b.isIntroducedInSession(PoliscoreUtil.SESSION) && billInterpreter.isInterpreted(b.getId())).collect(Collectors.toList())) {
			if (!ddb.exists(b.getId(), Bill.class)) {
				val interp = s3.get(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class).get();
				b.setInterpretation(interp);
				ddb.put(b);
				amount++;
			}
		}
		
		Log.info("Created " + amount + " missing bills in ddb from s3");
		
		// We don't need to worry about legislators here because this will happen as part of "interpretLegislators"
	}
	
	@SneakyThrows
	private void updateUscLegislators()
	{
		Log.info("Updating USC legislators resource files");
		
		File dbRes = new File(Environment.getDeployedPath(), "../../databuilder/src/main/resources");
		
		FileUtils.copyURLToFile(URI.create("https://theunitedstates.io/congress-legislators/legislators-current.json").toURL(), new File(dbRes, "legislators-current.json"));
		FileUtils.copyURLToFile(URI.create("https://theunitedstates.io/congress-legislators/legislators-historical.json").toURL(), new File(dbRes, "legislators-historical.json"));
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
		if (REINTERPRET_LEGISLATORS) {
			List<File> requests = legislatorRequestGenerator.process();
		
			if (requests.size() > 0) {
				List<File> responses = openAi.processBatch(requests);
				
				for (File f : responses) {
					responseImporter.process(f);
				}
			}
		} else {
			recalculateLegislators();
		}
	}
	
	/**
	 * Recalculates all legislator stats and bill interactions without actually re-interpreting their activity. Saves on AI interpretation costs while
	 * still allowing stats and interactions to remain up-to-date.
	 */
	private void recalculateLegislators() {
		for (var memLeg : memService.query(Legislator.class).stream()
				.filter(l -> l.isMemberOfSession(PoliscoreUtil.SESSION) && s3.exists(LegislatorInterpretation.generateId(l.getId()), LegislatorInterpretation.class))
				.collect(Collectors.toList()))
		{
			val opLeg = ddb.get(memLeg.getId(), Legislator.class);
			
			if (opLeg.isPresent()) {
				val leg = opLeg.get();
				
				leg.setInteractions(memLeg.getInteractions());
				
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
				
				val interp = s3.get(LegislatorInterpretation.generateId(leg.getId()), LegislatorInterpretation.class).get();
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
			} else {
				Log.error("Legislator " + memLeg.getName().getOfficial_full() + " (" + memLeg.getBioguideId() + ") was part of session but didnt exist in ddb?");
			}
		}
	}
	
	@SneakyThrows
	private void interpretPartyStats() {
		if (REINTERPRET_PARTIES) {
			List<File> requests = partyInterpreter.process();
			
			if (requests.size() > 0) {
				List<File> responses = openAi.processBatch(requests);
				
				for (File f : responses) {
					responseImporter.process(f);
				}
			}
		} else {
			partyInterpreter.process();
		}
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
	
	public static void main(String[] args) {
		Quarkus.run(DatabaseBuilder.class, args);
		Quarkus.asyncExit(0);
	}
}
