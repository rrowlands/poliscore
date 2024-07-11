package us.poliscore.entrypoint;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.quarkus.funqy.Funq;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.val;
import us.poliscore.model.Legislator;
import us.poliscore.model.LegislatorBillInteraction;
import us.poliscore.model.Legislator.LegislatorBillInteractionSet;
import us.poliscore.model.bill.Bill;
import us.poliscore.service.storage.CachedDynamoDbService;

@ApplicationScoped
public class Lambda {

    @Inject
    CachedDynamoDbService ddb;
    
    private List<Legislator> cachedLegislators = null;
    
    private List<Bill> cachedBills = null;

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
    	if (cachedLegislators != null) return cachedLegislators;
    	
    	var pageSize = 25;
    	if (queryParams.containsKey("pageSize")) pageSize = Integer.parseInt(queryParams.get("pageSize"));
    	
    	Boolean ascending = null;
    	if (queryParams.containsKey("ascending")) ascending = Boolean.parseBoolean(queryParams.get("ascending"));
    	
    	val legs = ddb.query(Legislator.class, pageSize, queryParams.get("index"), ascending, queryParams.get("exclusiveStartKey"));
    	
    	legs.forEach(l -> l.setInteractions(new LegislatorBillInteractionSet()));
    	
    	cachedLegislators = legs;
    	
    	return legs;
    }
    
    @Funq
    public Bill getBill(Map<String, String> queryParams)
    {
    	return ddb.get(queryParams.get("id"), Bill.class).orElse(null);
    }
    
    @Funq
    public List<Bill> getBills(Map<String, String> queryParams) {
    	if (cachedBills != null) return cachedBills;
    	
    	var pageSize = 25;
    	if (queryParams.containsKey("pageSize")) pageSize = Integer.parseInt(queryParams.get("pageSize"));
    	
    	Boolean ascending = null;
    	if (queryParams.containsKey("ascending")) ascending = Boolean.parseBoolean(queryParams.get("ascending"));
    	
    	val bills = ddb.query(Bill.class, pageSize, queryParams.get("index"), ascending, queryParams.get("exclusiveStartKey"));
    	
    	cachedBills = bills;
    	
    	return bills;
    }
}
