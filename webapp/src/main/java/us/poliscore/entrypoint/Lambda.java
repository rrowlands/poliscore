package us.poliscore.entrypoint;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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
import us.poliscore.PoliscoreUtil;
import us.poliscore.model.Legislator;
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
    
    private List<List<String>> cachedAllLegs;
    
    private Map<String, List<Legislator>> cachedLegislators = new HashMap<String, List<Legislator>>();
    
    private Map<String, List<Bill>> cachedBills = new HashMap<String, List<Bill>>();

    @GET
    @Path("getLegislator")
    public Legislator getLegislator(@NonNull @RestQuery String id) {
    	val op = ddb.get(id, Legislator.class);
    	
    	if (op.isPresent()) {
    		val leg = op.get();
    		leg.setInteractions(leg.getInteractions().stream()
    				.sorted(Comparator.comparing(LegislatorBillInteraction::getDate).reversed())
    				.limit(20).collect(Collectors.toCollection(LegislatorBillInteractionSet::new)));
    	}
    	
    	return op.orElse(null);
    }
    
    @GET
    @Path("/getLegislators")
    public List<Legislator> getLegislators(@RestQuery("pageSize") Integer _pageSize, @RestQuery("index") String _index, @RestQuery("ascending") Boolean _ascending, @RestQuery("exclusiveStartKey") String _exclusiveStartKey, @RestQuery String sortKey) {
    	val index = StringUtils.isNotBlank(_index) ? _index : Persistable.OBJECT_BY_DATE_INDEX;
    	val startKey = _exclusiveStartKey;
    	var pageSize = _pageSize == null ? 25 : _pageSize;
    	Boolean ascending = _ascending == null ? Boolean.TRUE : _ascending;
    	
    	val cacheable = StringUtils.isBlank(startKey) && pageSize == 25;
    	val cacheKey = index + "-" + ascending.toString() + "-" + sortKey;
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
    public LegislatorPageData getLegislatorPageData(@Context APIGatewayV2HTTPEvent event) {
    	String location = null;
    	
    	try {
	    	val sourceIp = event.getRequestContext().getHttp().getSourceIp();
	    	location = ipService.locateIp(sourceIp).orElse(null);
    	}
    	catch(Exception e) {
    		Log.error(e);
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
}
