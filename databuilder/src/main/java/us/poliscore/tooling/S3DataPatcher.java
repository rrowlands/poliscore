package us.poliscore.tooling;


import java.io.IOException;
import java.util.stream.Collectors;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.InterpretationOrigin;
import us.poliscore.model.IssueStats;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.service.BillInterpretationService;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.MemoryObjectService;

@QuarkusMain(name="S3DataPatcher")
public class S3DataPatcher implements QuarkusApplication {
	
	@Inject private LegislatorService legService;
	
	@Inject private BillService billService;
	
	@Inject
	private BillInterpretationService billInterpreter;
	
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
		billService.importBills();
		rollCallService.importUscVotes();
		
		s3.optimizeExists(BillInterpretation.class);
		
		long count = 0;
		
		for (var bill : memService.query(Bill.class).stream()
				.filter(b -> b.isIntroducedInSession(PoliscoreUtil.CURRENT_SESSION) && s3.exists(BillInterpretation.generateId(b.getId(), null, null), BillInterpretation.class)).collect(Collectors.toList()))
		{
			val interp = s3.get(BillInterpretation.generateId(bill.getId(), null, null), BillInterpretation.class).get();
			
			interp.setOrigin(InterpretationOrigin.POLISCORE);
			interp.setId(BillInterpretation.generateId(interp.getBillId(), interp.getOrigin(), null));
			
			for (int i = 0; i < interp.getSliceInterpretations().size(); ++i)
			{
				var slice = interp.getSliceInterpretations().get(i);
				slice.setOrigin(InterpretationOrigin.POLISCORE);
				slice.setId(BillInterpretation.generateId(slice.getBillId(), slice.getOrigin(), i));
				s3.put(slice);
				
				count++;
			}
			
			s3.put(interp);
			
			count++;
		}
		
		System.out.println("Program complete. Patched " + count + " interpretations");
	}
	
	@Override
	public int run(String... args) throws Exception {
	  process();
	  
	  Quarkus.waitForExit();
	  return 0;
	}
	
	public static void main(String[] args) {
		Quarkus.run(S3DataPatcher.class, args);
		Quarkus.asyncExit(0);
	}
}
