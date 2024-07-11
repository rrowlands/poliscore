package us.poliscore.entrypoint;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import io.quarkus.funqy.Funq;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.model.Legislator;
import us.poliscore.model.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.LegislatorBillInteraction;
import us.poliscore.model.Persistable;
import us.poliscore.model.bill.Bill;
import us.poliscore.service.storage.CachedDynamoDbService;

@ApplicationScoped
public class Lambda {

    @Inject
    CachedDynamoDbService ddb;
    
    private Map<String, List<Legislator>> cachedLegislators = new HashMap<String, List<Legislator>>();
    
    private Map<String, List<Bill>> cachedBills = new HashMap<String, List<Bill>>();

    @Funq
    public Legislator getLegislator(Map<String, String> queryParams) {
    	val op = ddb.get(queryParams.get("id"), Legislator.class);
    	
    	if (op.isPresent()) {
    		val leg = op.get();
    		leg.setInteractions(leg.getInteractions().stream()
    				.sorted(Comparator.comparing(LegislatorBillInteraction::getDate).reversed())
    				.limit(20).collect(Collectors.toCollection(LegislatorBillInteractionSet::new)));
    	}
    	
    	return op.orElse(null);
    }
    
    @Funq
    public List<Legislator> getLegislators(Map<String, String> queryParams) {
    	val index = queryParams.containsKey("index") ? queryParams.get("index") : Persistable.OBJECT_BY_DATE_INDEX;
    	val startKey = queryParams.get("exclusiveStartKey");
    	
    	var pageSize = 25;
    	if (queryParams.containsKey("pageSize")) pageSize = Integer.parseInt(queryParams.get("pageSize"));
    	
    	Boolean ascending = Boolean.TRUE;
    	if (queryParams.containsKey("ascending")) ascending = Boolean.parseBoolean(queryParams.get("ascending"));
    	
    	val cacheable = ascending && StringUtils.isBlank(startKey) && pageSize == 25;
    	if (cacheable && cachedLegislators.containsKey(index)) return cachedLegislators.get(index);
    	
    	val legs = ddb.query(Legislator.class, pageSize, index, ascending, startKey);
    	
    	legs.forEach(l -> l.setInteractions(new LegislatorBillInteractionSet()));
    	
    	if (cacheable) {
    		cachedLegislators.put(index, legs);
    	}
    	
    	return legs;
    }
    
    @Funq
    public Bill getBill(Map<String, String> queryParams)
    {
    	return ddb.get(queryParams.get("id"), Bill.class).orElse(null);
    }
    
    @Funq
    public List<Bill> getBills(Map<String, String> queryParams) {
    	val startKey = queryParams.get("exclusiveStartKey");
    	val index = queryParams.containsKey("index") ? queryParams.get("index") : Persistable.OBJECT_BY_DATE_INDEX;
    	
    	var pageSize = 25;
    	if (queryParams.containsKey("pageSize")) pageSize = Integer.parseInt(queryParams.get("pageSize"));
    	
    	Boolean ascending = Boolean.TRUE;
    	if (queryParams.containsKey("ascending")) ascending = Boolean.parseBoolean(queryParams.get("ascending"));
    	
    	val cacheable = ascending && StringUtils.isBlank(startKey) && pageSize == 25;
    	if (cacheable && cachedBills.containsKey(index)) return cachedBills.get(index);
    	
    	val bills = ddb.query(Bill.class, pageSize, queryParams.get("index"), ascending, startKey);
    	
    	if (cacheable) {
    		cachedBills.put(index, bills);
    	}
    	
    	return bills;
    }
}
