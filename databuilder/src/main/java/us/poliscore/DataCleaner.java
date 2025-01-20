package us.poliscore;

import java.io.IOException;
import java.util.ArrayList;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillText;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.MemoryObjectService;

@QuarkusMain(name="DataCleaner")
public class DataCleaner implements QuarkusApplication {
	
	@Inject private LegislatorService legService;
	
	@Inject private BillService billService;
	
	@Inject
	private RollCallService rollCallService;
	
	@Inject private MemoryObjectService memService;
	
	@Inject
	private LocalCachedS3Service s3;
	
	@Inject
	private DynamoDbPersistenceService ddb;
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		billService.importUscBills();
//		rollCallService.importUscVotes();
		
		val badInterps = new ArrayList<String>();
		val badExplanations = new ArrayList<String>();
		
		s3.optimizeExists(BillText.class);
		
		for (Bill b : memService.query(Bill.class)) {
			val op = s3.get(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class);
			
			if (!op.isPresent()) {
				if (s3.exists(BillText.generateId(b.getId()), BillText.class)) {
					badInterps.add(b.getId());
				}
				
				continue;
			}
			
			val interp = op.get();
			
			if (interp.getIssueStats() == null || !interp.getIssueStats().hasStat(TrackedIssue.OverallBenefitToSociety)) {
				badInterps.add(b.getId());
				continue;
			}
			
			val summaryHeaders = new String[] { "summary of the predicted impact to society and why", "summary of the predicted impact to society", "summary of the bill and predicted impact to society and why", "summary of the bill and predicted impact to society", "summary of the bill and its predicted impact to society and why", "summary of the bill and its predicted impact to society", "Summary of the bill's predicted impact to society and why", "Summary of the bill's predicted impact to society", "summary of predicted impact to society and why", "summary of predicted impact to society", "summary of the impact to society", "summary of impact to society", "summary report", "summary of the impact", "summary of impact", "summary", "explanation" };
			val summaryHeaderRegex = " *#*\\** *(" + String.join("|", summaryHeaders) + ") *#*\\** *:? *#*\\** *";
			if (interp.getLongExplain().matches("(?i)^" + summaryHeaderRegex + ".*$")) {
//				interp.getIssueStats().setExplanation(interp.getIssueStats().getExplanation().replaceFirst("(?i)" + summaryHeaderRegex, ""));
				badExplanations.add(b.getId());
				
//				s3.put(interp);
//				
//				b.setInterpretation(interp);
//				ddb.put(b);
			}
		}
		
		System.out.println(String.join(", ", badInterps.stream().map(id -> "\"" + id + "\"").toList()));
		System.out.println(String.join(", ", badExplanations.stream().map(id -> "\"" + id + "\"").toList()));
		
		System.out.println("Program complete.");
	}
	
	public static void main(String[] args) {
		Quarkus.run(DataCleaner.class, args);
	}
	
	@Override
	public int run(String... args) throws Exception {
	  process();
	  
	  Quarkus.waitForExit();
	  return 0;
	}
}
