package ch.poliscore;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import ch.poliscore.bill.BillTextPublishVersion;
import ch.poliscore.model.Bill;
import ch.poliscore.model.LegislatorBillInteration;
import ch.poliscore.service.BillInterpretationService;
import ch.poliscore.service.BillService;
import ch.poliscore.service.LegislatorService;
import ch.poliscore.service.PersistenceServiceIF;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;

/**
 * This bill interpreter interprets the last x bills for a given politician.
 */
//@QuarkusMain(name="LegislatorBillInterpreter")
@ApplicationScoped
public class LegislatorBillInterpreter implements QuarkusApplication
{
	@Inject
	private PersistenceServiceIF pService;
	
	@Inject
	private BillService billService;
	
	@Inject
	private BillInterpretationService interpService;
	
	@Inject
	private LegislatorService legService;
	
	
	public static void main(String[] args) {
		Quarkus.run(LegislatorBillInterpreter.class, args);
	}
	
	protected void process() throws IOException
	{
//		long totalBills = 0;
//		long totalVotes = 0;
		
		processLegislator(PoliscoreUtil.BERNIE_SANDERS_ID);
		
//		Log.info("USC import complete. Imported " + totalBills + " bills and " + totalVotes + " votes.");
		
//		Log.info("Printing Bernie Sanders for testing");
//		FileUtils.write(new File(Environment.getDeployedPath() + "../../bernie.json"), new ObjectMapper().valueToTree(pService.retrieve(PoliscoreUtil.BERNIE_SANDERS_ID, Legislator.class)).toPrettyString(), "UTF-8");
	}
	
	protected void processLegislator(String id)
	{
		val leg = legService.getById(id);
		
		for (val interact : leg.getInteractions().stream().sorted(Comparator.comparing(LegislatorBillInteration::getDate).reversed()).limit(10).collect(Collectors.toList()))
		{
			val bill = billService.getById(interact.getBillId());
			
			val text = getBillText(bill);
			
			if (text != null)
			{
				bill.setText(text);
				
				val interp = interpService.interpret(bill);
				
				interact.setIssueStats(interp.getIssueStats());
			}
			else
			{
				Log.info("Could not find text for bill " + bill.getId());
			}
		}
		
		legService.interpret(leg);
		
		legService.persist(leg);
	}
	
	@SneakyThrows
	protected String getBillText(Bill bill)
	{
		val parent = new File(PoliscoreUtil.APP_DATA, "bill-text/" + bill.getCongress() + "/" + bill.getType());
		
		val text = Arrays.asList(parent.listFiles()).stream()
				.filter(f -> f.getName().contains(bill.getCongress() + bill.getType() + bill.getNumber()))
				.sorted((a,b) -> BillTextPublishVersion.parseFromBillTextName(a.getName()).billMaturityCompareTo(BillTextPublishVersion.parseFromBillTextName(b.getName())))
				.findFirst();
		
		if (text.isPresent())
		{
			return FileUtils.readFileToString(text.get(), "UTF-8");
		}
		else
		{
			return null;
		}
	}
	
	@Override
    public int run(String... args) throws Exception {
//		if (args.length > 0) throw new RuntimeException("Must provide a path to the bill dump.");
//		
//		final File dumpParent = new File(args[0]);
//		if (!dumpParent.exists()) throw new RuntimeException("Expected parent file argument to exist");
		
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
