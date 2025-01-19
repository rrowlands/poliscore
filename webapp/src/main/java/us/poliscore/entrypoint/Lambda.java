package us.poliscore.entrypoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.jboss.resteasy.reactive.RestQuery;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.LegislatorBillLinker;
import us.poliscore.LegislatorPageData;
import us.poliscore.Page;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Persistable;
import us.poliscore.model.TrackedIssue;
import us.poliscore.model.bill.Bill;
import us.poliscore.model.bill.BillInterpretation;
import us.poliscore.model.legislator.Legislator;
import us.poliscore.model.legislator.Legislator.LegislatorBillInteractionList;
import us.poliscore.model.legislator.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.legislator.LegislatorBillInteraction;
import us.poliscore.model.session.SessionInterpretation;
import us.poliscore.service.IpGeolocationService;
import us.poliscore.service.storage.DynamoDbPersistenceService;

@Path("")
@RequestScoped
public class Lambda {
	
	public static final String TRACKED_ISSUE_INDEX = "~ti~";

    @Inject
    DynamoDbPersistenceService ddb;
//    CachedDynamoDbService ddb;
    
    @Inject
    IpGeolocationService ipService;
    
    @Inject
    ObjectMapper mapper;
    
    private static List<List<String>> cachedAllLegs;
    
    private static Map<String, List<Legislator>> cachedLegislators = new HashMap<String, List<Legislator>>();
    
    private static Map<String, List<Bill>> cachedBills = new HashMap<String, List<Bill>>();
    
    private static List<Bill> allBillsDump;
    
    private static List<List<String>> allBillsIndex;
    
    @GET
    @Path("getSessionStats")
    public SessionInterpretation getSessionStats() {
    	val op = ddb.get(SessionInterpretation.generateId(PoliscoreUtil.CURRENT_SESSION.getNumber()), SessionInterpretation.class);
    	
    	return op.orElse(null);
    }
    
    @GET
    @Path("getLegislator")
    public Legislator getLegislator(@NonNull @RestQuery String id, @RestQuery("pageSize") Integer _pageSize, @RestQuery("index") String _index, @RestQuery("ascending") Boolean _ascending, @RestQuery("exclusiveStartKey") Integer _exclusiveStartKey, @RestQuery String sortKey) {
    	val index = StringUtils.isNotBlank(_index) ? _index : Persistable.OBJECT_BY_DATE_INDEX;
    	var pageSize = _pageSize == null ? 25 : _pageSize;
    	Boolean ascending = _ascending == null ? Boolean.TRUE : _ascending;
    	int exclusiveStartKey = (_exclusiveStartKey == null) ? -1 : _exclusiveStartKey;
    	
    	val op = ddb.get(id, Legislator.class);
    	
    	if (op.isPresent()) {
    		val leg = op.get();
    		
    		linkInterpBills(leg);
    		
//    		leg.setInteractions(leg.getInteractions().stream()
//    				.sorted(Comparator.comparing(LegislatorBillInteraction::getDate).reversed())
//    				.limit(25).collect(Collectors.toCollection(LegislatorBillInteractionSet::new)));
    		
    		var page = filterInteractions(leg, index, sortKey, pageSize, ascending, exclusiveStartKey);
    		
    		leg.setInteractions(page.getData().get(0));
    	}
    	
    	return op.orElse(null);
    }
    
    private void linkInterpBills(Legislator leg) {
//		try
//		{
//			var exp = leg.getInterpretation().getLongExplain();
//			
//			for (val interact : leg.getInteractions().stream().sorted(Comparator.comparingInt((LegislatorBillInteraction b) -> b.getBillName().length()).reversed()).collect(Collectors.toList())) {
//				val url = "/" + PoliscoreUtil.DEPLOYMENT_YEAR + "/bill/" + interact.getBillId().replace(Bill.ID_CLASS_PREFIX + "/" + LegislativeNamespace.US_CONGRESS.getNamespace() + "/" + PoliscoreUtil.CURRENT_SESSION.getNumber() + "/", "");
//				
//				var billName = interact.getBillName();
//				if (billName.endsWith(".")) billName = billName.substring(0, billName.length() - 1);
//				billName = billName.strip();
//				if (billName.endsWith("."))
//					billName = billName.substring(0, billName.length() - 1);
//				
//				val billId = Bill.billTypeFromId(interact.getBillId()).getName() + "-" + Bill.billNumberFromId(interact.getBillId());
//				
//				val billMatchPattern = "(" + Pattern.quote(billName) + "|" + Pattern.quote(billId) + ")[^\\d]";
//				
//				Pattern pattern = Pattern.compile("(?i)" + billMatchPattern + "", Pattern.CASE_INSENSITIVE);
//			    Matcher matcher = pattern.matcher(exp);
//			    while (matcher.find()) {
//			    	exp = exp.replaceFirst(matcher.group(1), "<a href=\"" + url + "\" >" + billName + "</a>");
//			    }
//			}
//			
//			leg.getInterpretation().setLongExplain(exp);
//		} catch (Throwable t) {
//			Log.error(t);
//		}
    	
    	LegislatorBillLinker.linkInterpBills(leg);
    }
    
    @GET
    @Path("/getLegislatorInteractions")
    public Page<LegislatorBillInteractionList> getLegislatorInteractions(@RestQuery("id") String id, @RestQuery("pageSize") Integer _pageSize, @RestQuery("index") String _index, @RestQuery("ascending") Boolean _ascending, @RestQuery("exclusiveStartKey") Integer _exclusiveStartKey, @RestQuery String sortKey) {
    	val index = StringUtils.isNotBlank(_index) ? _index : Persistable.OBJECT_BY_DATE_INDEX;
    	var pageSize = _pageSize == null ? 25 : _pageSize;
    	Boolean ascending = _ascending == null ? Boolean.TRUE : _ascending;
    	int exclusiveStartKey = (_exclusiveStartKey == null) ? -1 : _exclusiveStartKey;

    	val op = ddb.get(id, Legislator.class);
    	
    	if (op.isPresent()) {
    		val leg = op.get();
    		
    		return filterInteractions(leg, index, sortKey, pageSize, ascending, exclusiveStartKey);
    	}
    	
    	Page<LegislatorBillInteractionList> page = new Page<LegislatorBillInteractionList>();
    	page.setData(Arrays.asList());
    	page.setExclusiveStartKey(exclusiveStartKey);
    	page.setHasMoreData(false);
    	return page;
    }
    
    private Page<LegislatorBillInteractionList> filterInteractions(Legislator leg, String index, String sortKey, Integer pageSize, Boolean ascending, Integer exclusiveStartKey)
    {
    	Page<LegislatorBillInteractionList> page = new Page<LegislatorBillInteractionList>();
    	page.setData(Arrays.asList());
    	page.setExclusiveStartKey(exclusiveStartKey);
    	
    	val interacts = new LegislatorBillInteractionList();
    	page.setData(Arrays.asList(interacts));
    	
    	var stream = leg.getInteractions().stream();
		
		Comparator<LegislatorBillInteraction> comparator;
		if (index.equals(Persistable.OBJECT_BY_DATE_INDEX)) {
			comparator = Comparator.comparing(LegislatorBillInteraction::getDate);
		} else if (index.equals(Persistable.OBJECT_BY_RATING_INDEX)) {
			comparator = Comparator.comparing(LegislatorBillInteraction::getRating);
		} else if (index.equals(Persistable.OBJECT_BY_IMPORTANCE_INDEX)) {
			comparator = Comparator.comparing(LegislatorBillInteraction::getImportance);
		} else if (index.equals("TrackedIssue")) {
			var issue = TrackedIssue.valueOf(sortKey);
			stream = stream.filter(lbi -> lbi.getIssueStats().hasStat(issue));
			comparator = (LegislatorBillInteraction a, LegislatorBillInteraction b) -> a.getIssueStats().getStat(issue).compareTo(b.getIssueStats().getStat(issue));
		} else {
			throw new UnsupportedOperationException(index);
		}
		
		if (ascending) {
			stream = stream.sorted(comparator);
		} else {
			stream = stream.sorted(comparator.reversed());
		}
		
		var allInteracts = stream.collect(Collectors.toList());
		
		for (int i = exclusiveStartKey + 1; i < Math.min(allInteracts.size(), exclusiveStartKey + 1 + pageSize); ++i) {
			interacts.add(allInteracts.get(i));
		}
		
		page.setHasMoreData((interacts.size() + 1 + exclusiveStartKey) < leg.getInteractions().size());
		
		return page;
    }
    
    @GET
    @Path("/getLegislators")
    public List<Legislator> getLegislators(@RestQuery("pageSize") Integer _pageSize, @RestQuery("index") String _index, @RestQuery("ascending") Boolean _ascending, @RestQuery("exclusiveStartKey") String _exclusiveStartKey, @RestQuery String sortKey) {
    	val index = StringUtils.isNotBlank(_index) ? _index : Persistable.OBJECT_BY_DATE_INDEX;
    	val startKey = _exclusiveStartKey;
    	var pageSize = _pageSize == null ? 25 : _pageSize;
    	Boolean ascending = _ascending == null ? Boolean.TRUE : _ascending;
    	
    	val cacheable = StringUtils.isBlank(startKey) && pageSize == 25;
    	val cacheKey = index + "-" + ascending.toString() + (StringUtils.isBlank(sortKey) ? "" : "-" + sortKey);
    	if (cacheable && cachedLegislators.containsKey(cacheKey)) return cachedLegislators.get(cacheKey);
    	
    	val legs = ddb.query(Legislator.class, pageSize, index, ascending, startKey, sortKey);
    	
    	legs.forEach(l -> l.setInteractions(new LegislatorBillInteractionList()));
    	
    	if (cacheable) {
    		cachedLegislators.put(cacheKey, legs);
    	}
    	
    	return legs;
    }
    
    @GET
    @SneakyThrows
    @Path("/getLegislatorPageData")
    public LegislatorPageData getLegislatorPageData(@Context APIGatewayV2HTTPEvent event, @RestQuery("state") String state) {
    	String location = null;
    	
    	if (StringUtils.isNotBlank(state)) {
    		location = state.toUpperCase();
    	} else {
	    	try {
		    	val sourceIp = event.getRequestContext().getHttp().getSourceIp();
		    	location = ipService.locateIp(sourceIp).orElse(null);
	    	}
	    	catch(Exception e) {
	    		Log.error(e);
	    	}
    	}
    	
    	String index = (location == null ? null : Persistable.OBJECT_BY_LOCATION_INDEX);
    	
    	val legs = getLegislators(null, index, null, null, location);
    	
    	return new LegislatorPageData(location, legs, getAllLegs());
    }
    
    @SuppressWarnings("unchecked")
	@SneakyThrows
    private List<List<String>> getAllLegs() {
    	if (cachedAllLegs == null) {
    		cachedAllLegs = PoliscoreUtil.getObjectMapper().readValue(IOUtils.toString(Lambda.class.getResourceAsStream("/legislators.index"), "UTF-8"), List.class);
    	}
    	
    	return cachedAllLegs;
    }
    
    @GET
    @Path("/getBill")
    public Bill getBill(@NonNull @RestQuery("id") String id)
    {
    	val b = ddb.get(id, Bill.class).orElse(null);
    	
    	if (b != null && b.getInterpretation() != null) {
    		// Unfortunately the SliceInterpretations's "start" and "end" fields (e.g. /bill[1]/legis-body[1]/title[3]/section[15]) are confusing Google.
    		// Google thinks that it's a URL and is trying to follow it. Because we don't really use this data anyway (at the moment), we're going to just null it out for now.
    		b.getInterpretation().setSliceInterpretations(new ArrayList<BillInterpretation>());
    	}
    	
    	return b;
    }
    
    @GET
    @Path("/getBills")
    @SneakyThrows
    public List<Bill> getBills(@RestQuery("pageSize") Integer _pageSize, @RestQuery("index") String _index, @RestQuery("ascending") Boolean _ascending, @RestQuery("exclusiveStartKey") String _exclusiveStartKey, @RestQuery String sortKey) {
    	val index = StringUtils.isNotBlank(_index) ? _index : Persistable.OBJECT_BY_DATE_INDEX;
    	val startKey = _exclusiveStartKey;
    	var pageSize = _pageSize == null ? 25 : _pageSize;
    	Boolean ascending = _ascending == null ? Boolean.TRUE : _ascending;
    	
    	val cacheable = StringUtils.isBlank(startKey) && pageSize == 25 && StringUtils.isBlank(sortKey) && !index.startsWith(TRACKED_ISSUE_INDEX);
    	val cacheKey = index + "-" + ascending.toString();
    	if (cacheable && cachedBills.containsKey(cacheKey)) return cachedBills.get(cacheKey);
    	
    	List<Bill> bills;
    	if (index.startsWith(TRACKED_ISSUE_INDEX)) {
    		val issue = TrackedIssue.valueOf(index.replace(TRACKED_ISSUE_INDEX, ""));
    		bills = getBillsDump().stream()
    				.filter(b -> b.getInterpretation().getIssueStats().hasStat(issue))
    				.sorted((a,b) -> Integer.valueOf(b.getInterpretation().getIssueStats().getStat(issue)).compareTo(a.getInterpretation().getIssueStats().getStat(issue)))
    				.limit(pageSize)
    				.toList();
    	} else {
    		bills = ddb.query(Bill.class, pageSize, index, ascending, startKey, sortKey);
    	}
    	
    	if (cacheable) {
    		cachedBills.put(cacheKey, bills);
    	}
    	
    	return bills;
    }
    
    @SuppressWarnings("unchecked")
    @SneakyThrows
    public List<Bill> getBillsDump() {
    	if (allBillsDump == null) {
    		allBillsDump = mapper.readValue(IOUtils.toString(Lambda.class.getResourceAsStream("/allbills.dump"), "UTF-8"), new TypeReference<List<Bill>>() {});
    	}
    	
    	return allBillsDump;
    }
    
    @SuppressWarnings("unchecked")
    @SneakyThrows
    public List<List<String>> getBillsIndex() {
    	if (allBillsIndex == null) {
    		allBillsIndex = mapper.readValue(IOUtils.toString(Lambda.class.getResourceAsStream("/bills.index"), "UTF-8"), List.class);
    	}
    	
    	return allBillsIndex;
    }
    
    @GET
    @Path("/queryBills")
    public List<List<String>> queryBills(@RestQuery("text") String text) {
    	val bills = new ArrayList<List<String>>(getBillsIndex());
    	
//    	bills.addAll(Arrays.asList(TrackedIssue.values()).stream().map(i -> Arrays.asList(TRACKED_ISSUE_INDEX + i.name(), i.getName() + " (issue)")).toList());
    	
    	return bills.stream()
    			.filter(b -> b.get(1).toLowerCase().trim().contains(text.toLowerCase().trim()) || b.get(0).toLowerCase().trim().contains(text.toLowerCase().trim()))
    			.sorted((a,b) -> LevenshteinDistance.getDefaultInstance().apply(a.get(1), text) - LevenshteinDistance.getDefaultInstance().apply(b.get(1), text))
    			.limit(30)
    			.collect(Collectors.toList());
    }
}
