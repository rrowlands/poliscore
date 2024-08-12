package us.poliscore;


import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jboss.resteasy.reactive.RestQuery;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.entrypoint.Lambda;
import us.poliscore.model.CongressionalSession;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Legislator;
import us.poliscore.model.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.LegislatorBillInteraction;
import us.poliscore.model.Persistable;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.bill.BillType;
import us.poliscore.service.IpGeolocationService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.LocalCachedS3Service;
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
	private DynamoDbPersistenceService ddb;
	
	@Inject
	private LocalCachedS3Service s3;
	
	@Inject
    IpGeolocationService ipService;
	
	public static List<String> PROCESS_BILL_TYPE = Arrays.asList(BillType.values()).stream().filter(bt -> !BillType.getIgnoredBillTypes().contains(bt)).map(bt -> bt.getName().toLowerCase()).collect(Collectors.toList());
	
	protected void process() throws IOException
	{
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
	
	private void linkInterpBills(Legislator leg) {
		try
		{
			var exp = leg.getInterpretation().getIssueStats().getExplanation();
			
			// Standardize terminology from H.J. Res XXX -> HJRES-XXX
			exp = exp.replaceAll("H\\.?J\\.? ?Res\\.? ?-?(\\d{1,4})", "HJRES-$1");
			exp = exp.replaceAll("S\\.?J\\.? ?Res\\.? ?-?(\\d{1,4})", "SJRES-$1");
			exp = exp.replaceAll("H\\.?R\\.? ?-?(\\d{1,4})", "HR-$1");
			exp = exp.replaceAll("S\\.? ?-?(\\d{1,4})", "S-$1");
			
			
			// Replace 
			for (val interact : leg.getInteractions()) {
				val url = "/bill" + interact.getBillId().replace(Bill.ID_CLASS_PREFIX + "/" + LegislativeNamespace.US_CONGRESS.getNamespace(), "");
				
				exp = exp.replaceAll("(?i)" + Pattern.quote(interact.getBillName()), "<a href=\"" + url + "\" >" + interact.getBillName() + "</a>");
			}
			
			leg.getInterpretation().getIssueStats().setExplanation(exp);
		} catch (Throwable t) {
			Log.error(t);
		}
    }
	
	public Page<LegislatorBillInteractionSet> getLegislatorInteractions(@RestQuery("id") String id, @RestQuery("exclusiveStartKey") Integer exclusiveStartKey) {
    	val pageSize = 20;
    	
    	if (exclusiveStartKey == null) exclusiveStartKey = -1;

    	val set = new LegislatorBillInteractionSet();
    	Page<LegislatorBillInteractionSet> page = new Page<LegislatorBillInteractionSet>();
    	page.setData(Arrays.asList(set));
    	page.setExclusiveStartKey(exclusiveStartKey);
    	
    	val op = ddb.get(id, Legislator.class);
    	
    	if (op.isPresent()) {
    		val leg = op.get();
    		
    		val allInteracts = leg.getInteractions().stream()
				.sorted(Comparator.comparing(LegislatorBillInteraction::getDate).reversed())
				.collect(Collectors.toList());
    		
			for (int i = exclusiveStartKey + 1; i < Math.min(allInteracts.size(), exclusiveStartKey + 1 + pageSize); ++i) {
				set.add(allInteracts.get(i));
			}
			
			page.setHasMoreData((set.size() + 1 + exclusiveStartKey) < leg.getInteractions().size());
    	}
    	
    	return page;
    }
	
	@SneakyThrows
	public List<List<String>> queryBills(@RestQuery("text") String text) {
		List<List<String>> cachedAllBills = PoliscoreUtil.getObjectMapper().readValue(IOUtils.toString(Lambda.class.getResourceAsStream("/bills.index"), "UTF-8"), List.class);
		
    	return cachedAllBills.stream()
    			.filter(b -> b.get(1).toLowerCase().contains(text.toLowerCase()) || b.get(0).toLowerCase().contains(text.toLowerCase()))
    			.limit(30)
    			.collect(Collectors.toList());
    }
	
	public List<Bill> getBills(@RestQuery("pageSize") Integer _pageSize, @RestQuery("index") String _index, @RestQuery("ascending") Boolean _ascending, @RestQuery("exclusiveStartKey") String _exclusiveStartKey, @RestQuery String sortKey) {
		val index = StringUtils.isNotBlank(_index) ? _index : Persistable.OBJECT_BY_DATE_INDEX;
    	val startKey = _exclusiveStartKey;
    	var pageSize = _pageSize == null ? 25 : _pageSize;
    	Boolean ascending = _ascending == null ? Boolean.TRUE : _ascending;
    	
    	List<Bill> bills;
//    	if (index.startsWith(Lambda.TRACKED_ISSUE_INDEX)) {
//    		val issue = TrackedIssue.valueOf(index.replace(Lambda.TRACKED_ISSUE_INDEX, ""));
//    		bills = Lambda.getBillsDump().stream()
//    				.filter(b -> b.getInterpretation().getIssueStats().hasStat(issue))
//    				.sorted((a,b) -> Integer.valueOf(b.getInterpretation().getIssueStats().getStat(issue)).compareTo(a.getInterpretation().getIssueStats().getStat(issue)))
//    				.toList();
//    	} else {
    		bills = ddb.query(Bill.class, pageSize, index, ascending, startKey, sortKey);
//    	}
    	
    	return bills;
    }
	
	public List<Legislator> getLegislators(Integer _pageSize, String _index, Boolean _ascending, String _exclusiveStartKey, String sortKey) {
    	val index = StringUtils.isNotBlank(_index) ? _index : Persistable.OBJECT_BY_DATE_INDEX;
    	val startKey = _exclusiveStartKey;
    	var pageSize = _pageSize == null ? 25 : _pageSize;
    	Boolean ascending = _ascending == null ? Boolean.TRUE : _ascending;
    	
    	val legs = ddb.query(Legislator.class, pageSize, index, ascending, startKey, sortKey);
    	
    	legs.forEach(l -> l.setInteractions(new LegislatorBillInteractionSet()));
    	
    	return legs;
    }
	
	@SneakyThrows
	public List<List<String>> getLegislatorPageData() {
    	@SuppressWarnings("unchecked")
		List<List<String>> allLegs = PoliscoreUtil.getObjectMapper().readValue(IOUtils.toString(Lambda.class.getResourceAsStream("/legislators.index"), "UTF-8"), List.class);
    	
    	return allLegs;
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
