package us.poliscore.entrypoint;


import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillType;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryPersistenceService;

@QuarkusMain(name="DataSandbox")
public class DataSandbox implements QuarkusApplication
{
	@Inject
	private MemoryPersistenceService memService;
	
	@Inject
	private LocalFilePersistenceService localStore;
	
	@Inject
	private DynamoDbPersistenceService ddb;
	
	@Inject
	private LocalCachedS3Service s3;
	
	@Inject
	private LegislatorService legService;
	
	@Inject
	private BillService billService;
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	protected void process() throws IOException
	{
		legService.importLegislators();
		billService.importUscBills();
		
//		val obj = dynamoDb.get("BIL/us/congress/118/hr/4763", Bill.class).orElseThrow();
//		
//		System.out.println(PoliscoreUtil.getObjectMapper().valueToTree(obj));
		
		
		
//		val legs = dynamoDb.query(Legislator.class, 25, null, null, null);
//		System.out.println(legs.size());
//		System.out.println(PoliscoreUtil.getObjectMapper().valueToTree(legs));
		
		
//		val leg = dynamoDb.get(Legislator.generateId(LegislativeNamespace.US_CONGRESS, "F000480"), Legislator.class);
//		System.out.println(PoliscoreUtil.getObjectMapper().valueToTree(leg));
		
		
//		val out = getLegislatorPageData();
		
		
//		String sourceIp = "71.56.241.71";
//		val location = ipService.locateIp(sourceIp).orElse(null);
////		String location = "CO";
//    	val out = getLegislators(10, (location == null ? null : Persistable.OBJECT_BY_LOCATION_INDEX), true, "LEG/us/congress/C001134~`~CO/8", null);
    	
		
//		val date = "1980-12-23";
//		val out = getLegislators(null, Persistable.OBJECT_BY_DATE_INDEX, null, null, null);
		
//		val out = getBills(25, Persistable.OBJECT_BY_DATE_INDEX, false, null, null);
		
		
//		val out = queryBills("gun");
    	
//    	val out = getLegislatorInteractions(PoliscoreUtil.BERNIE_SANDERS_ID, 19);
		
//		val out = ddb.get(Legislator.generateId(LegislativeNamespace.US_CONGRESS, "K000402"), Legislator.class).orElseThrow();
		
//		val out = leg.getInteractions();
		
//		val out = leg.calculateTopInteractions();
		
//		linkInterpBills(leg);
		
//		val out = leg.getInterpretation().getIssueStats().getExplanation();
		
		
		
//		val out = memService.query(Bill.class).stream()
//			.filter(b -> b.getInterpretation() == null || b.getInterpretation().getIssueStats() == null || !b.getInterpretation().getIssueStats().hasStat(TrackedIssue.OverallBenefitToSociety))
//			.map(b -> b.getId())
//			.toList();		
		
		
//		val out = getBills(25, Lambda.TRACKED_ISSUE_INDEX + TrackedIssue.NationalDefense.name(), false, null, null);
		
		s3.optimizeExists(BillInterpretation.class);
		
		val out = memService.query(Bill.class).stream()
				.filter(b -> b.isIntroducedInSession(CongressionalSession.S118) && s3.exists(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class))
				.toList().size();
		
		
    	
    	System.out.println(PoliscoreUtil.getObjectMapper().valueToTree(out));
//		System.out.println(out);
		
		
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
