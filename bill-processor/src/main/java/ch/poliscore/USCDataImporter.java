package ch.poliscore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
@QuarkusMain
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
	
	
	public static void main(String[] args) {
		Quarkus.run(USCDataImporter.class, args);
	}
	
	protected void process(File uscData) throws IOException
	{
		legService.importLegislators();
		
		for (File fCongress : Arrays.asList(uscData.listFiles()).stream()
				.filter(f -> f.getName().matches("\\d+") && f.isDirectory())
				.sorted((a,b) -> a.getName().compareTo(b.getName()))
				.collect(Collectors.toList()))
		{
			Log.info("Processing " + fCongress.getName() + " congress");
			
			int count = 0;
			for (File data : allFilesWhere(new File(fCongress, "bills"), f -> f.getName().equals("data.json")))
			{
				try (var fos = new FileInputStream(data))
				{
					billService.importUscData(fos);
					count++;
				}
			}
			Log.info("Imported " + count + " bills");
			
			count = 0;
			for (File data : allFilesWhere(new File(fCongress, "votes"), f -> f.getName().equals("data.json")))
			{
				try (var fos = new FileInputStream(data))
				{
					rollCallService.importUscData(fos);
					count++;
				}
			}
			Log.info("Imported " + count + " votes");
		}
		
		Log.info("USC import complete.");
		
		Log.info("Printing Bernie Sanders for testing");
		new ObjectMapper().writeValue(System.out, pService.retrieve("S000033", Legislator.class));
	}
	
	public List<File> allFilesWhere(File parent, Predicate<File> criteria)
	{
		List<File> all = new ArrayList<File>();
		
		if (!parent.isDirectory()) return all;
		
		for (File child : parent.listFiles())
		{
			if (child.isDirectory())
			{
				all.addAll(allFilesWhere(child, criteria));
			}
			else if (criteria.test(child))
			{
				all.add(child);
			}
		}
		
		return all;
	}
	
	@Override
    public int run(String... args) throws Exception {
//		if (args.length > 0) throw new RuntimeException("Must provide a path to the bill dump.");
//		
//		final File dumpParent = new File(args[0]);
//		if (!dumpParent.exists()) throw new RuntimeException("Expected parent file argument to exist");
		
		val data = "/Users/rrowlands/dev/projects/congress/data";
		
        process(new File(data));
        
        Quarkus.waitForExit();
        return 0;
    }
}
