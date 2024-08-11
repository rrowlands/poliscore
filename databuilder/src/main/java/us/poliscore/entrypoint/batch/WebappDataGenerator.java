package us.poliscore.entrypoint.batch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

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
import us.poliscore.model.LegislatorInterpretation;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
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
	
	public static final String[] states = new String[] {
		"KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "MP", "AL", "AK", "AZ", "AR", "AS", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "GU", "HI", "ID", "IL", "IN", "IA", "KS", "OH", "OK", "OR", "PA", "PR", "RI", "SC", "SD", "TN", "TX", "TT", "UT", "VT", "VA", "VI", "WA", "WV", "WI", "WY" 
	};
	
	public static void main(String[] args) {
		Quarkus.run(WebappDataGenerator.class, args);
	}
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		billService.importUscBills();
		
		s3.optimizeExists(BillInterpretation.class);
		s3.optimizeExists(LegislatorInterpretation.class);
		
		legService.generateLegislatorWebappIndex();
		billService.generateBillWebappIndex();
//		billService.dumbAllBills();
		
		generateRoutes();
		generateSiteMap();
		
		System.out.println("Program complete.");
	}
	
	@SneakyThrows
	private void generateRoutes() {
		final File out = new File(Environment.getDeployedPath(), "../../webapp/src/main/webui/routes.txt");
		val routes = new ArrayList<String>();
		
		// All states
		Arrays.asList(states).stream().forEach(s -> routes.add("/legislators/state/" + s));
		
		// All legislator routes
		routes.add("/legislators");
		memService.query(Legislator.class).stream()
			.filter(l -> l.isMemberOfSession(CongressionalSession.S118) && s3.exists(LegislatorInterpretation.generateId(l.getId()), LegislatorInterpretation.class))
			.sorted((a,b) -> a.getDate().compareTo(b.getDate()))
			.forEach(l -> routes.add("/legislator/" + l.getBioguideId()));
		
		// All bills
		routes.add("/bills");
		memService.query(Bill.class).stream()
			.filter(b -> b.isIntroducedInSession(CongressionalSession.S118) && s3.exists(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class))
			.sorted((a,b) -> a.getDate().compareTo(b.getDate()))
			.forEach(b -> routes.add("/bill/" + b.getCongress() + "/" + b.getType().getName().toLowerCase() + "/" + b.getNumber()));
		
		
		FileUtils.write(out, String.join("\n", routes), "UTF-8");
	}
	
	@SneakyThrows
	private void generateSiteMap() {
		final String url = "https://poliscore.us";
		final File out = new File(Environment.getDeployedPath(), "../../webapp/src/main/webui/src/assets/sitemap.txt");
		val routes = new ArrayList<String>();
		
		// All states
		Arrays.asList(states).stream().forEach(s -> routes.add(url + "/legislators/state/" + s));
		
		// All legislator routes
		routes.add(url + "/legislators");
		memService.query(Legislator.class).stream()
			.filter(l -> l.isMemberOfSession(CongressionalSession.S118) && s3.exists(LegislatorInterpretation.generateId(l.getId()), LegislatorInterpretation.class))
			.sorted((a,b) -> a.getDate().compareTo(b.getDate()))
			.forEach(l -> routes.add(url + "/legislator/" + l.getBioguideId()));
		
		// All bills
		routes.add(url + "/bills");
		memService.query(Bill.class).stream()
			.filter(b -> b.isIntroducedInSession(CongressionalSession.S118) && s3.exists(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class))
			.sorted((a,b) -> a.getDate().compareTo(b.getDate()))
			.forEach(b -> routes.add(url + "/bill/" + b.getCongress() + "/" + b.getType().getName().toLowerCase() + "/" + b.getNumber()));
		
		
		FileUtils.write(out, String.join("\n", routes), "UTF-8");
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
