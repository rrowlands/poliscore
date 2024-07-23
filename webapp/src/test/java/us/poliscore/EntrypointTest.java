package us.poliscore;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import us.poliscore.dynamodb.DdbBuilder;
import us.poliscore.model.Persistable;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.MemoryPersistenceService;

@QuarkusTest
public class EntrypointTest {
	
	@Inject
	private MemoryPersistenceService memory;
	
	@Inject
	private DynamoDbPersistenceService ddb;
	
	@Inject
	private DdbBuilder ddbb;
	
	@Test
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
	
	
//	@Test
//    public void testGetLegislatorInteractions() throws Exception {
//        // you test your lambdas by invoking on http://localhost:8081
//        // this works in dev mode too
//		
//		ddbb.defaultTestData();
//		
//        given()
//        		.queryParam("id", PoliscoreUtil.BERNIE_SANDERS_ID)
//                .get("/getLegislatorInteractions")
//                .then()
//                .statusCode(200)
//                .body(containsString("Safe"));
//    }
	
	
	// Not currently testable since I can't get index creation to work on localstack
//	@Test
//    public void testGetLegislators() throws Exception {
//        // you test your lambdas by invoking on http://localhost:8081
//        // this works in dev mode too
//		
//		ddbb.defaultTestData();
//		
//        given()
//        		.queryParam("pageSize", 25)
//        		.queryParam("index", Persistable.OBJECT_BY_LOCATION_INDEX)
//        		.queryParam("ascending", true)
//        		.queryParam("sortKey", "VT")
//                .get("/getLegislators")
//                .then()
//                .statusCode(200)
//                .body(containsString("Bernard Sanders"));
//    }

	
	
}
