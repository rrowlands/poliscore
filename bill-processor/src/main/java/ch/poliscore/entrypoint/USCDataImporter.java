package ch.poliscore.entrypoint;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import ch.poliscore.PoliscoreUtil;
import ch.poliscore.interpretation.BillType;
import ch.poliscore.model.Legislator;
import ch.poliscore.service.BillService;
import ch.poliscore.service.LegislatorInterpretationService;
import ch.poliscore.service.LegislatorService;
import ch.poliscore.service.RollCallService;
import ch.poliscore.service.storage.LocalFilePersistenceService;
import ch.poliscore.service.storage.MemoryPersistenceService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.val;

/**
 * This bulk importer is designed to import a full dataset built with the github.com/unitedstates/congress toolkit 
 */
@QuarkusMain(name="USCDataImporter")
public class USCDataImporter implements QuarkusApplication
{
	@Inject
	private MemoryPersistenceService memService;
	
	@Inject
	private LocalFilePersistenceService localStore;
	
	@Inject
	private BillService billService;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private RollCallService rollCallService;
	
	@Inject
	private LegislatorInterpretationService legInterp;
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	public static void main(String[] args) {
		Quarkus.run(USCDataImporter.class, args);
	}
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		
		/*
		long totalBills = 0;
		long totalVotes = 0;
		
		for (File fCongress : Arrays.asList(PoliscoreUtil.USC_DATA.listFiles()).stream()
				.filter(f -> f.getName().matches("\\d+") && f.isDirectory())
				.sorted((a,b) -> a.getName().compareTo(b.getName()))
				.collect(Collectors.toList()))
		{
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
			legInterp.getOrCreate(legId);
			
			val legislator = memService.retrieve(legId, Legislator.class).orElseThrow();
			
			localStore.store(legislator);
		}
		
		Log.info("USC import complete. Imported " + totalBills + " bills and " + totalVotes + " votes.");
		*/
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}