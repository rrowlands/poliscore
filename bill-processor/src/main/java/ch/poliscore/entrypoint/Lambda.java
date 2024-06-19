package ch.poliscore.entrypoint;

import java.util.Map;

import ch.poliscore.model.Legislator;
import ch.poliscore.service.storage.DynamoDBPersistenceService;
import io.quarkus.funqy.Funq;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class Lambda {

    @Inject
    DynamoDBPersistenceService ddb;

    // https://quarkus.io/guides/funqy-http
    @Funq
    public Legislator getLegislator(Map<String, String> queryParams) {
    	return ddb.retrieve(queryParams.get("id"), Legislator.class).orElseThrow();
    }
}
