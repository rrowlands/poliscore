package us.poliscore.entrypoint;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.bill.CBOBillAnalysis;
import us.poliscore.service.BillInterpretationService;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorInterpretationService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryPersistenceService;

/**
 * TODO : This really only provides half of what we need in order to do a real budget analysis. The other half of the equation is "how much value will it provide"
 *   or, how much "should" it cost. Simply knowing how much a bill costs, without knowing how much value it would provide, would encourage people to think "all
 *   spending is bad spending" and can be misleading since it's only covering half of the equation.
 * 
 * This importer is designed to fetch data from the Congressional Budget Office. The process for doing so is as follows:
 * 
 *  1. Read the XML RSS feed for the congressional session to find the list of bills, e.g. https://www.cbo.gov/rss/118congress-cost-estimates.xml
 *  2. Request the HTML page referenced in the XML "link".
 *  3. This HTML page contains a link to a PDF document, download it
 *  4. The PDF document contains within it a table which contains standardized budget infromation.
 */
@QuarkusMain(name="CBODataFetcher")
public class CBODataFetcher implements QuarkusApplication
{
	public static final boolean CHECK_EXISTS = true;
	
	public static final int TIMEOUT = 4000; // in ms
	
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
	private LegislatorInterpretationService legInterp;
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	public static void main(String[] args) {
		Quarkus.run(CBODataFetcher.class, args);
	}
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		billService.importUscBills();
		rollCallService.importUscVotes();
		
		if (CHECK_EXISTS) s3.optimizeExists(CBOBillAnalysis.class);
		
		long count = 0;
		
		val doc = fetchWithRetry("https://www.cbo.gov/rss/" + PoliscoreUtil.SESSION.getNumber() + "congress-cost-estimates.xml");
		
		for (val element : doc.select("response item")) {
			if (StringUtils.isBlank(element.child(4).text()) || StringUtils.isBlank(element.child(2).text())) continue;
			
			val billType = getBillType(element.child(4).text());
			val billNum = getBillNumber(element.child(4).text(), billType);
			if (billType == null || billNum == null) continue;
			val billId = Bill.generateId(PoliscoreUtil.SESSION.getNumber(), billType, billNum);
			
			if (CHECK_EXISTS && s3.exists(CBOBillAnalysis.generateId(billId), CBOBillAnalysis.class)) { count++; continue; }
			
			val publication = fetchWithRetry(element.child(2).text());
			var summary = publication.select("div#cost-estimate-landing-page article div.field--type-text-with-summary").text();
			
			if (StringUtils.isBlank(summary) || !summary.contains("would cost")) {
				val glanceTable = publication.select("section.summary div.field--name-field-estimate-table table.at-a-glance");
				
				if (glanceTable.size() == 0 || StringUtils.isBlank(glanceTable.text())) { System.out.println("Summary not available for " + billId + ". Check to make sure it exists at " + element.child(2).text()); continue; }
				
				summary = glanceTable.outerHtml();
			}
			
			val a = new CBOBillAnalysis();
			a.setBillId(billId);
			a.setSummary(summary);
			s3.put(a);
			
			count++;
		}
		
		Log.info("Program complete. " + count + " bills have analysis from the CBO.");
	}
	
	private Document fetchWithRetry(String url) {
		return fetchWithRetry(url, 0);
	}
	
	@SneakyThrows
	private Document fetchWithRetry(String url, int retries) {
		try {
			return Jsoup.parse(URI.create(url).toURL(), TIMEOUT);
		} catch (SocketTimeoutException ste) {
			if (retries < 3) {
				return fetchWithRetry(url, retries + 1);
			} else {
				throw ste;
			}
		}
	}
	
	private Integer getBillNumber(String name, BillType type) {
		try {
			return Integer.valueOf(name.replaceAll("[^\\d]", ""));
		} catch (Exception e) {
			Log.error("Unable to parse bill number from " + name, e);
			return null;
		}
	}
	
	private BillType getBillType(String name) {
		try {
			val clean = name.toUpperCase().replace(".", "").replace(" ", "").replaceAll("[\\d]", "");
			
			return BillType.valueOf(clean);
		} catch (IllegalArgumentException e) {
			Log.error("Unable to parse bill type from " + name, e);
			return null;
		}
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
