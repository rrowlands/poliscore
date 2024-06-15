package ch.poliscore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.poliscore.model.Legislator;
import ch.poliscore.service.BillService;
import ch.poliscore.service.LegislatorService;
import ch.poliscore.service.PersistenceServiceIF;
import ch.poliscore.service.RollCallService;
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
	private PersistenceServiceIF pService;
	
	@Inject
	private BillService billService;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private RollCallService rollCallService;
	
	@Inject
	private LegislatorBillInterpreter legInterp;
	
	public static void main(String[] args) {
		Quarkus.run(USCDataImporter.class, args);
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
			Log.info("Processing " + fCongress.getName() + " congress");
			
			int count = 0;
			for (File data : PoliscoreUtil.allFilesWhere(new File(fCongress, "bills"), f -> f.getName().equals("data.json")))
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
			for (File data : PoliscoreUtil.allFilesWhere(new File(fCongress, "votes"), f -> f.getName().equals("data.json")))
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
		
		legInterp.process();
		
		Log.info("USC import complete. Imported " + totalBills + " bills and " + totalVotes + " votes.");
		
		Log.info("Printing Bernie Sanders for testing to " + new File(PoliscoreUtil.APP_DATA, "bernie.json").getAbsolutePath());
		FileUtils.write(new File(PoliscoreUtil.APP_DATA, "bernie.json"), new ObjectMapper().valueToTree(pService.retrieve(PoliscoreUtil.BERNIE_SANDERS_ID, Legislator.class)).toPrettyString(), "UTF-8");
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
