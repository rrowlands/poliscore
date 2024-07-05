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
import us.poliscore.MissingBillTextException;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.Legislator;
import us.poliscore.model.LegislatorBillInteraction;
import us.poliscore.model.bill.Bill;
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
				
				for (File data : PoliscoreUtil.allFilesWhere(new File(fCongress, "bills/" + bt), f -> f.getName().equals("data.json")))
				{
					try (var fos = new FileInputStream(data))
					{
						billService.importUscData(fos);
						totalBills++;
					}
				}
				Log.info("Imported " + totalBills + " bills");
			}
			
			int skipped = 0;
			for (File data : PoliscoreUtil.allFilesWhere(new File(fCongress, "votes"), f -> f.getName().equals("data.json")))
			{
				try (var fos = new FileInputStream(data))
				{
					if (rollCallService.importUscData(fos))
						totalVotes++;
					else
						skipped++;
				}
			}
			Log.info("Imported " + totalVotes + " votes. Skipped " + skipped);
		}
		
		interpretLegislators();
		
//		val bills = memService.query(Bill.class).stream()
//				.sorted(Comparator.comparing(Bill::getIntroducedDate).reversed())
//				.limit(400)
//				.filter(b -> 
//					billService.hasBillText(b)) //  && !billInterpreter.isInterpreted(b.getId())
//				.limit(50)
//				.toList();
//		System.out.println("Set to interpret " + bills.stream().filter(b -> !billInterpreter.isInterpreted(b.getId())).count() + " bills.");
//		bills.forEach(b -> {
//			billInterpreter.getOrCreate(b.getId());
//		});
//		
//		memService.query(Legislator.class).stream()
//			.filter(l -> l.getBirthday() != null)
//			.sorted(Comparator.comparing(Legislator::getBirthday).reversed())
//			.limit(400)
//			.filter(l -> l.getInteractions().stream().anyMatch(i -> billInterpreter.isInterpreted(i.getBillId())))
//			.limit(10)
//			.forEach(l -> {
//			interpretLegislator(l.getId());
//		});
		
		Log.info("Poliscore database build complete. Imported " + totalBills + " bills and " + totalVotes + " votes.");
	}

	/**
	 * Interprets and loads a set of legislators
	 */
	private void interpretLegislators() {
//		for (String legId : PoliscoreUtil.SPRINT_1_LEGISLATORS)
//			for (Legislator leg : memService.query(Legislator.class).stream().limit(10).toList())
			String legId = memService.get(PoliscoreUtil.MIKE_JOHNSON_ID, Legislator.class).get().getId();
		{
			interpretLegislator(legId);
		}
	}
	
	private void interpretLegislator(String legId) {
		val interp = legInterp.getOrCreate(legId);
		interp.getIssueStats().setExplanation(interp.getIssueStats().getExplanation());
		
		val legislator = memService.get(legId, Legislator.class).orElseThrow();
		legislator.setInterpretation(interp);
		
		int interpretedBills = 0;
		for (val interact : legislator.getInteractions().stream()
				.sorted(Comparator.comparing(LegislatorBillInteraction::getDate).reversed())
				.limit(100)
				.filter(i -> billInterpreter.isInterpreted(i.getBillId()))
				.collect(Collectors.toList()))
		{
			try
			{
				val billInterp = billInterpreter.getByBillId(interact.getBillId()).get();
				interact.setIssueStats(billInterp.getIssueStats());
				
				val bill = billService.getById(interact.getBillId()).get();
				bill.setInterpretation(billInterp);
				
				dynamoDb.put(bill);
				interpretedBills++;
			}
			catch (NoSuchElementException e)
			{
				Log.error("Could not find interpretation for bill " + interact.getBillId());
			}
			catch (MissingBillTextException ex)
			{
				// TODO
				Log.error("Could not find text for bill " + interact.getBillId());
			}
		}
		
		if (interpretedBills > 0)
			dynamoDb.put(legislator);
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
