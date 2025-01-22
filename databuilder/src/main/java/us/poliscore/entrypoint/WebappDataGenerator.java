package us.poliscore.entrypoint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.Environment;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.LegislatorInterpretation;
import us.poliscore.service.BillInterpretationService;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.MemoryObjectService;

/**
 * Generates static resources for consumption by the webapp project 
 */
@QuarkusMain(name="WebappDataGenerator")
public class WebappDataGenerator implements QuarkusApplication
{
	@Inject
	private MemoryObjectService memService;
	
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
		Quarkus.asyncExit(0);
	}
	
	public void process() throws IOException
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
		
		Log.info("Webapp Data Generator complete.");
	}
	
	@SneakyThrows
	private void generateRoutes() {
		final File out = new File(Environment.getDeployedPath(), "../../webapp/src/main/webui/routes.txt");
		val routes = new ArrayList<String>();
		
		// Party Stats
		routes.add("/congress/democrat");
		routes.add("/congress/republican");
		routes.add("/congress/independent");
		
		// All states
		Arrays.asList(states).stream().forEach(s -> routes.add("/legislators/state/" + s.toLowerCase()));
		
		// Sorted Legislators
		routes.add("/legislators/byimpact/descending");
		routes.add("/legislators/byimpact/ascending");
		routes.add("/legislators/byrating/descending");
		routes.add("/legislators/byrating/ascending");
		routes.add("/legislators/byage/descending");
		routes.add("/legislators/byage/ascending");
		
		// All legislator routes
		routes.add("/legislators");
		memService.query(Legislator.class).stream()
			.filter(l -> l.isMemberOfSession(PoliscoreUtil.CURRENT_SESSION) && s3.exists(LegislatorInterpretation.generateId(l.getId(), PoliscoreUtil.CURRENT_SESSION.getNumber()), LegislatorInterpretation.class))
			.sorted((a,b) -> a.getDate().compareTo(b.getDate()))
			.forEach(l -> routes.add("/legislator/" + l.getBioguideId()));
		
		// All bills
		routes.add("/bills");
		memService.query(Bill.class).stream()
			.filter(b -> b.isIntroducedInSession(PoliscoreUtil.CURRENT_SESSION) && s3.exists(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class))
			.sorted((a,b) -> a.getDate().compareTo(b.getDate()))
			.forEach(b -> routes.add("/bill/" + b.getType().getName().toLowerCase() + "/" + b.getNumber()));
		
		// Sorted Bills
		routes.add("/bills/byrating/descending");
		routes.add("/bills/byrating/ascending");
		routes.add("/bills/bydate/descending");
		routes.add("/bills/bydate/ascending");
		
		FileUtils.write(out, String.join("\n", routes), "UTF-8");
	}
	
	@SneakyThrows
	private void generateSiteMap() {
		final String url = "https://poliscore.us";
		final File out = new File(Environment.getDeployedPath(), "../../webapp/src/main/webui/src/assets/sitemap.txt");
		val routes = new ArrayList<String>();
		
		for (var congress : PoliscoreUtil.SUPPORTED_CONGRESSES)
		{
			int lastYearOfSession = 1789 + (congress.getNumber() * 2) - 1;
			String prefix = "/" + lastYearOfSession;
			
			routes.add(url + prefix + "/about");
			
			// Party Stats
			routes.add(url + prefix + "/congress/democrat");
			routes.add(url + prefix + "/congress/republican");
			routes.add(url + prefix + "/congress/independent");
			
			// All states
			Arrays.asList(states).stream().forEach(s -> routes.add(url + prefix + "/legislators/state/" + s.toLowerCase()));
			
			// All legislator routes
			routes.add(url + prefix + "/legislators");
			memService.query(Legislator.class).stream()
				.filter(l -> l.isMemberOfSession(congress) && s3.exists(LegislatorInterpretation.generateId(l.getId(), congress.getNumber()), LegislatorInterpretation.class))
				.sorted((a,b) -> a.getDate().compareTo(b.getDate()))
				.forEach(l -> routes.add(url + prefix + "/legislator/" + l.getBioguideId()));
			
			// All bills
			routes.add(url + prefix + "/bills");
			memService.query(Bill.class).stream()
				.filter(b -> b.isIntroducedInSession(congress) && s3.exists(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class))
				.sorted((a,b) -> a.getDate().compareTo(b.getDate()))
				.forEach(b -> routes.add(url + prefix + "/bill/" + b.getType().getName().toLowerCase() + "/" + b.getNumber()));
		}
		
		FileUtils.write(out, String.join("\n", routes), "UTF-8");
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
