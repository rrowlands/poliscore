package us.poliscore.entrypoint;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.Legislator;
import us.poliscore.model.LegislatorBillInteraction;
import us.poliscore.model.bill.BillType;
import us.poliscore.service.BillInterpretationService;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryPersistenceService;

/**
 * This bulk importer is designed to import a full dataset built with the github.com/unitedstates/congress toolkit 
 */
@QuarkusMain(name="DatabaseBuilder")
public class DatabaseBuilder implements QuarkusApplication
{
	@Inject
	private MemoryPersistenceService memService;
	
	@Inject
	private LocalFilePersistenceService localStore;
	
	@Inject
	private DynamoDbPersistenceService dynamoDb;
	
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
		Quarkus.run(DatabaseBuilder.class, args);
	}
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		
		long totalBills = 0;
		long totalVotes = 0;
		
		for (File fCongress : Arrays.asList(PoliscoreUtil.USC_DATA.listFiles()).stream()
				.filter(f -> f.getName().matches("\\d+") && f.isDirectory())
				.sorted((a,b) -> a.getName().compareTo(b.getName()))
				.collect(Collectors.toList()))
		{
			if (!PoliscoreUtil.SUPPORTED_CONGRESSES.contains(Integer.valueOf(fCongress.getName()))) continue;
			
			Log.info("Processing " + fCongress.getName() + " congress");
			
			for (val bt : PROCESS_BILL_TYPE)
			{
				Log.info("Processing bill types " + bt + " congress");
				
				File fBillType = new File(fCongress, "bills/" + bt);
			
				int count = 0;
				for (File data : PoliscoreUtil.allFilesWhere(fBillType, f -> f.getName().equals("data.json")))
				{
					try (var fos = new FileInputStream(data))
					{
						billService.importUscData(fos);
						count++;
						totalBills++;
					}
				}
				Log.info("Imported " + count + " bills");
				
				count = 0;
				for (File data : PoliscoreUtil.allFilesWhere(fBillType, f -> f.getName().equals("data.json")))
				{
					try (var fos = new FileInputStream(data))
					{
						rollCallService.importUscData(fos);
						count++;
						totalVotes++;
					}
				}
				Log.info("Imported " + count + " votes");
			}
		}
		
		// Interpret legislators
		for (String legId : PoliscoreUtil.SPRINT_1_LEGISLATORS)
		{
			val interp = legInterp.getOrCreate(legId);
			interp.getIssueStats().setExplanation(interp.getIssueStats().getExplanation());
			
			val legislator = memService.retrieve(legId, Legislator.class).orElseThrow();
			legislator.setInterpretation(interp);
			
			int persisted = 0;
			for (val interact : legislator.getInteractions().stream().sorted(Comparator.comparing(LegislatorBillInteraction::getDate).reversed()).collect(Collectors.toList()))
			{
				if (persisted >= LegislatorInterpretationService.LIMIT_BILLS) break;
				
				try
				{
					val billInterp = billInterpreter.getById(interact.getBillId()).get();
					interact.setIssueStats(billInterp.getIssueStats());
					
					val bill = billService.getById(interact.getBillId()).get();
					bill.setInterpretation(billInterp);
					
					dynamoDb.store(bill);
					
					persisted++;
				}
				catch (NoSuchElementException ex)
				{
					// TODO
					Log.error("Could not find text for bill " + interact.getBillId());
				}
			}
			
			dynamoDb.store(legislator);
		}
		
		Log.info("Poliscore database build complete. Imported " + totalBills + " bills and " + totalVotes + " votes.");
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
