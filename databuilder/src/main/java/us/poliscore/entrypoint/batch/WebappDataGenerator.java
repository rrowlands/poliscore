package us.poliscore.entrypoint.batch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.Environment;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.Legislator;
import us.poliscore.model.bill.BillType;
import us.poliscore.service.BillInterpretationService;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.MemoryPersistenceService;

/**
 * Generates static resources for consumption by the webapp project 
 */
@QuarkusMain(name="WebappDataGenerator")
public class WebappDataGenerator implements QuarkusApplication
{
	@Inject
	private MemoryPersistenceService memService;
	
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
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	public static void main(String[] args) {
		Quarkus.run(WebappDataGenerator.class, args);
	}
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		billService.importUscBills();
		
		legService.generateLegislatorWebappIndex();
		billService.generateBillWebappIndex();
		
		generateRoutes();
		
		System.out.println("Program complete.");
	}
	
	@SneakyThrows
	private void generateRoutes() {
		final File out = new File(Environment.getDeployedPath(), "../../webapp/src/main/webui/routes.txt");
		val routes = new ArrayList<String>();
		
		memService.query(Legislator.class).stream()
			.filter(l -> l.isMemberOfSession(CongressionalSession.S118))
			.sorted((a,b) -> a.getDate().compareTo(b.getDate()))
			.forEach(l -> routes.add("/legislator/" + l.getBioguideId()));
		
		FileUtils.write(out, String.join("\n", routes), "UTF-8");
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}