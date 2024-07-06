package us.poliscore.entrypoint;

import java.util.List;
import java.util.Map;

import io.quarkus.funqy.Funq;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import us.poliscore.model.Legislator;
import us.poliscore.model.bill.Bill;
import us.poliscore.service.storage.DynamoDbPersistenceService;

@ApplicationScoped
public class Lambda {

    @Inject
    DynamoDbPersistenceService ddb;

    @Funq
    public Legislator getLegislator(Map<String, String> queryParams) {
    	return ddb.get(queryParams.get("id"), Legislator.class).orElse(null);
    }
    
    @Funq
    public List<Legislator> getLegislators(Map<String, String> queryParams) {
    	return ddb.query(Legislator.class, 25, queryParams.get("exclusiveStartKey"));
    }
    
    @Funq
    public Bill getBill(Map<String, String> queryParams)
    {
    	return ddb.get(queryParams.get("id"), Bill.class).orElse(null);
    }
    
    @Funq
    public List<Bill> getBills(Map<String, String> queryParams) {
    	return ddb.query(Bill.class, 25, queryParams.get("exclusiveStartKey"));
    }
}
