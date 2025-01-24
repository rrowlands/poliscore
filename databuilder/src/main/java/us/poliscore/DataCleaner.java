package us.poliscore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Collectors;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillText;
import us.poliscore.model.legislator.Legislator;
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
		rollCallService.importUscVotes();
		
		for (var leg : memService.query(Legislator.class).stream()
				.filter(l -> l.isMemberOfSession(PoliscoreUtil.CURRENT_SESSION)) //  && s3.exists(LegislatorInterpretation.generateId(l.getId(), PoliscoreUtil.CURRENT_SESSION.getNumber()), LegislatorInterpretation.class)
				.collect(Collectors.toList()))
		{
			ddb.delete(leg);
		}
		
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
