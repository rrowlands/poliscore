package us.poliscore;


import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.model.Legislator;
import us.poliscore.model.bill.BillType;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryPersistenceService;

@QuarkusMain(name="Sandbox")
public class Sandbox implements QuarkusApplication
{
	@Inject
	private MemoryPersistenceService memService;
	
	@Inject
	private LocalFilePersistenceService localStore;
	
	@Inject
	private DynamoDbPersistenceService dynamoDb;
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	protected void process() throws IOException
	{
//		val obj = dynamoDb.get("BIL/us/congress/118/hr/4763", Bill.class).orElseThrow();
//		
//		System.out.println(PoliscoreUtil.getObjectMapper().valueToTree(obj));
		
		
		
		val legs = dynamoDb.query(Legislator.class, 25, null);
		
		System.out.println(PoliscoreUtil.getObjectMapper().valueToTree(legs));
	}
	
	public static void main(String[] args) {
		Quarkus.run(Sandbox.class, args);
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
