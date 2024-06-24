package ch.poliscore;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import org.junit.jupiter.api.Test;

import ch.poliscore.dynamodb.DdbBuilder;
import ch.poliscore.service.LegislatorService;
import ch.poliscore.service.storage.DynamoDBPersistenceService;
import ch.poliscore.service.storage.MemoryPersistenceService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

//@QuarkusTest
public class EntrypointTest {

	@Inject
	private LegislatorService legService;
	
	@Inject
	private MemoryPersistenceService memory;
	
	@Inject
	private DynamoDBPersistenceService ddb;
	
	@Inject
	private DdbBuilder ddbb;
	
//	@Test
    public void testGetLegislator() throws Exception {
        // you test your lambdas by invoking on http://localhost:8081
        // this works in dev mode too
		
		ddbb.defaultTestData();
		
        given()
        		.queryParam("id", PoliscoreUtil.BERNIE_SANDERS_ID)
                .get("/getLegislator")
                .then()
                .statusCode(200)
                .body(containsString("Bernard Sanders"));
    }

}
