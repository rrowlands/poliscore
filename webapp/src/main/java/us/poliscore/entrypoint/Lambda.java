package us.poliscore.entrypoint;

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

import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.val;
import us.poliscore.LegislatorPageData;
import us.poliscore.Page;
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.LegislativeNamespace;
import us.poliscore.model.Legislator;
import us.poliscore.model.Legislator.CongressionalChamber;
import us.poliscore.model.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.LegislatorBillInteraction;
import us.poliscore.model.Persistable;
import us.poliscore.model.bill.Bill;
import us.poliscore.service.IpGeolocationService;
import us.poliscore.service.storage.CachedDynamoDbService;

@Path("")
@RequestScoped
public class Lambda {

    @Inject
    CachedDynamoDbService ddb;
    
    @Inject
    IpGeolocationService ipService;
    
    private static List<List<String>> cachedAllLegs;
    
    private static Map<String, List<Legislator>> cachedLegislators = new HashMap<String, List<Legislator>>();
    
    private static Map<String, List<Bill>> cachedBills = new HashMap<String, List<Bill>>();
    
    private static List<List<String>> cachedAllBills;

    @GET
    @Path("getLegislator")
    public Legislator getLegislator(@NonNull @RestQuery String id) {
    	val op = ddb.get(id, Legislator.class);
    	
    	if (op.isPresent()) {
    		val leg = op.get();
    		
    		linkInterpBills(leg);
    		
    		leg.setInteractions(leg.getInteractions().stream()
    				.sorted(Comparator.comparing(LegislatorBillInteraction::getDate).reversed())
    				.limit(20).collect(Collectors.toCollection(LegislatorBillInteractionSet::new)));
    	}
    	
    	return op.orElse(null);
    }
    
    private void linkInterpBills(Legislator leg) {
		try
		{
			var exp = leg.getInterpretation().getIssueStats().getExplanation();
			
			// Standardize terminology from H.J. Res XXX -> HJRES-XXX
//			if (leg.getTerms().last().getChamber().equals(CongressionalChamber.SENATE)) {
//				exp = exp.replaceAll("S(\\.|-) ?(\\d{1,4})", "S-$1");
//				exp = exp.replaceAll("S\\.?J\\.? ?(Res)?\\.? ?-?(\\d{1,4})", "SJRES-$2");
//			} else {
//				exp = exp.replaceAll("H\\.?J\\.? ?(Res)?\\.? ?-?(\\d{1,4})", "HJRES-$2");
//				exp = exp.replaceAll("H\\.?R\\.? ?-?(\\d{1,4})", "HR-$1");
//			}
			
			
			// Replace 
			for (val interact : leg.getInteractions()) {
				val url = "/bill" + interact.getBillId().replace(Bill.ID_CLASS_PREFIX + "/" + LegislativeNamespace.US_CONGRESS.getNamespace(), "");
				
//				if (interact.getBillName().matches("(?i) of \\d\\d\\d\\d$") && exp.toLowerCase().contains(interact.getBillName().substring(0, interact.getBillName().length() - 8).toLowerCase())) {
//					val shortName = interact.getBillName().substring(0, interact.getBillName().length() - 8);
//					exp = exp.replaceAll("(?i)" + Pattern.quote(shortName), "<a href=\"" + url + "\" >" + shortName + "</a>");
//				} else {
					exp = exp.replaceAll("(?i)" + Pattern.quote(interact.getBillName()), "<a href=\"" + url + "\" >" + interact.getBillName() + "</a>");
//				}
			}
			
			leg.getInterpretation().getIssueStats().setExplanation(exp);
		} catch (Throwable t) {
			Log.error(t);
		}
    }
    
    @GET
    @Path("/getLegislatorInteractions")
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
    	
    	legs.forEach(l -> l.setInteractions(new LegislatorBillInteractionSet()));
    	
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
    	return ddb.get(id, Bill.class).orElse(null);
    }
    
    @GET
    @Path("/getBills")
    public List<Bill> getBills(@RestQuery("pageSize") Integer _pageSize, @RestQuery("index") String _index, @RestQuery("ascending") Boolean _ascending, @RestQuery("exclusiveStartKey") String _exclusiveStartKey, @RestQuery String sortKey) {
    	val index = StringUtils.isNotBlank(_index) ? _index : Persistable.OBJECT_BY_DATE_INDEX;
    	val startKey = _exclusiveStartKey;
    	var pageSize = _pageSize == null ? 25 : _pageSize;
    	Boolean ascending = _ascending == null ? Boolean.TRUE : _ascending;
    	
    	val cacheable = StringUtils.isBlank(startKey) && pageSize == 25 && StringUtils.isBlank(sortKey);
    	val cacheKey = index + "-" + ascending.toString();
    	if (cacheable && cachedBills.containsKey(cacheKey)) return cachedBills.get(cacheKey);
    	
    	val bills = ddb.query(Bill.class, pageSize, index, ascending, startKey, sortKey);
    	
    	if (cacheable) {
    		cachedBills.put(cacheKey, bills);
    	}
    	
    	return bills;
    }
    
    @SuppressWarnings("unchecked")
    @SneakyThrows
    private List<List<String>> getAllBills() {
    	if (cachedAllBills == null) {
    		cachedAllBills = PoliscoreUtil.getObjectMapper().readValue(IOUtils.toString(Lambda.class.getResourceAsStream("/bills.index"), "UTF-8"), List.class);
    	}
    	
    	return cachedAllBills;
    }
    
    @GET
    @Path("/queryBills")
    public List<List<String>> queryBills(@RestQuery("text") String text) {
    	return getAllBills().stream()
    			.filter(b -> b.get(1).toLowerCase().contains(text.toLowerCase()) || b.get(0).toLowerCase().contains(text.toLowerCase()))
    			.sorted((a,b) -> LevenshteinDistance.getDefaultInstance().apply(a.get(1), text) - LevenshteinDistance.getDefaultInstance().apply(b.get(1), text))
    			.limit(30)
    			.collect(Collectors.toList());
    }
}
