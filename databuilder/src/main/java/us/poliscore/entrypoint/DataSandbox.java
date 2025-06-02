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
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillSlice;
import us.poliscore.model.bill.BillText;
import us.poliscore.model.bill.BillType;
import us.poliscore.model.session.SessionInterpretationOld;
import us.poliscore.parsing.BillSlicer;
import us.poliscore.parsing.XMLBillSlicer;
import us.poliscore.service.BillService;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
import us.poliscore.service.storage.LocalFilePersistenceService;
import us.poliscore.service.storage.MemoryObjectService;

@QuarkusMain(name="DataSandbox")
public class DataSandbox implements QuarkusApplication
{
	@Inject
	private MemoryObjectService memService;
	
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
		billService.importBills();
		
		
//		val sessionStats = new SessionInterpretation();
//		sessionStats.setSession(PoliscoreUtil.CURRENT_SESSION.getNumber());
//		val stats = s3.get(sessionStats.getId(), SessionInterpretation.class).get();
//		stats.getDemocrat().getStats().getStats().remove(TrackedIssue.SocialEquity);
//		stats.getRepublican().getStats().getStats().remove(TrackedIssue.SocialEquity);
//		stats.getIndependent().getStats().getStats().remove(TrackedIssue.SocialEquity);
//		
//		s3.put(sessionStats);
		
		
//		s3.optimizeExists(BillInterpretation.class);
//		s3.optimizeExists(BillText.class);
//		for(val bill : memService.query(Bill.class))
//		{
//			if (!s3.exists(bill.getId().replace(Bill.ID_CLASS_PREFIX, BillInterpretation.ID_CLASS_PREFIX), BillInterpretation.class)
//				|| !s3.exists(bill.getId().replace(Bill.ID_CLASS_PREFIX, BillText.ID_CLASS_PREFIX), BillText.class))
//				continue;
//			
//			val interp = s3.get(bill.getId().replace(Bill.ID_CLASS_PREFIX, BillInterpretation.ID_CLASS_PREFIX), BillInterpretation.class).get();
//			
//			for (var sliceInterp : interp.getSliceInterpretations())
//			{
//				sliceInterp.getIssueStats().getStats().remove(TrackedIssue.SocialEquity);
//				s3.put(sliceInterp);
//			}
//			
//			interp.getIssueStats().getStats().remove(TrackedIssue.SocialEquity);
//			
//			s3.put(interp);
//		}
		
		
//		for(val bill : memService.query(Bill.class))
//		{
//			val op = s3.get(bill.getId().replace(Bill.ID_CLASS_PREFIX, BillInterpretation.ID_CLASS_PREFIX), BillInterpretation.class);
//			
//			if (op.isPresent())
//			{
////				val old = op.get().getId();
//				
////				op.get().setId(LegislatorInterpretation.generateId(leg.getId(), PoliscoreUtil.CURRENT_SESSION.getNumber()));
//				op.get().getIssueStats().getStats().remove(TrackedIssue.SocialEquity);
//				
////				System.out.println(old + " migrated to " + op.get().getId());
//				s3.put(op.get());
////				System.out.println(PoliscoreUtil.getObjectMapper().writeValueAsString(op.get()));
//				
//				
////				op.get()
//			}
//		}
		
		
//		for(val leg : memService.query(Legislator.class))
//		{
//			val op = s3.get(leg.getId().replace(Legislator.ID_CLASS_PREFIX, LegislatorInterpretation.ID_CLASS_PREFIX), LegislatorInterpretation.class);
//			
//			if (op.isPresent())
//			{
////				val old = op.get().getId();
//				
////				op.get().setId(LegislatorInterpretation.generateId(leg.getId(), PoliscoreUtil.CURRENT_SESSION.getNumber()));
//				op.get().getIssueStats().getStats().remove(TrackedIssue.SocialEquity);
//				
////				System.out.println(old + " migrated to " + op.get().getId());
//				s3.put(op.get());
////				System.out.println(PoliscoreUtil.getObjectMapper().writeValueAsString(op.get()));
//				
//				
////				op.get()
//			}
//		}
		
		
		
		
		
		/*
		 * 
		 */
//		val op = ddb.get(SessionInterpretation.generateId(118), SessionInterpretation.class);
//		if (StringUtils.isBlank(op.get().getDemocrat().getLongExplain()))
//		{
//			System.out.println("Democrat is blank");
//		}
//		if (StringUtils.isBlank(op.get().getRepublican().getLongExplain()))
//		{
//			System.out.println("Republican is blank");
//		}
//		if (StringUtils.isBlank(op.get().getIndependent().getLongExplain()))
//		{
//			System.out.println("Independent is blank");
//		}
		
		
		
		
		
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
		
//		s3.optimizeExists(BillInterpretation.class);
//		
//		val out = memService.query(Bill.class).stream()
//				.filter(b -> b.isIntroducedInSession(CongressionalSession.S118) && s3.exists(BillInterpretation.generateId(b.getId(), null), BillInterpretation.class))
//				.toList().size();
//		
//		
//    	
//    	System.out.println(PoliscoreUtil.getObjectMapper().valueToTree(out));
//		System.out.println(out);
		
		
		System.out.println("Program Complete");
	}
	
	public static void main(String[] args) {
		Quarkus.run(DataSandbox.class, args);
		Quarkus.asyncExit(0);
	}
	
	@Override
    public int run(String... args) throws Exception {
        process();
        
        Quarkus.waitForExit();
        return 0;
    }
}
