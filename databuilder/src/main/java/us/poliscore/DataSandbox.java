package us.poliscore;

import java.io.IOException;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.RollCallService;
import us.poliscore.service.storage.MemoryPersistenceService;

@QuarkusMain(name="DataSandbox")
public class DataSandbox implements QuarkusApplication {
	
	@Inject private LegislatorService legService;
	
	@Inject private BillService billService;
	
	@Inject
	private RollCallService rollCallService;
	
	@Inject private MemoryPersistenceService memService;
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		billService.importUscBills();
//		rollCallService.importUscVotes();
		
		System.out.println("Program complete.");
	}
	
	public static void main(String[] args) {
		Quarkus.run(DataSandbox.class, args);
	}
	
	@Override
	public int run(String... args) throws Exception {
	  process();
	  
	  Quarkus.waitForExit();
	  return 0;
	}
}
