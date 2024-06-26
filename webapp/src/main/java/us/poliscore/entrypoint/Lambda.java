package us.poliscore.entrypoint;

import java.util.List;
import java.util.Map;

import io.quarkus.funqy.Funq;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import us.poliscore.model.Legislator;
import us.poliscore.service.storage.DynamoDbPersistenceService;

@ApplicationScoped
public class Lambda {

    @Inject
    DynamoDbPersistenceService ddb;

    // https://quarkus.io/guides/funqy-http
    @Funq
    public Legislator getLegislator(Map<String, String> queryParams) {
    	return ddb.retrieve(queryParams.get("id"), Legislator.class).orElseThrow();
    }
    
    @Funq
    public List<Legislator> getLegislators(Map<String, String> queryParams) {
    	return ddb.query(Legislator.class);
    }
}
