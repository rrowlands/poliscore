package us.poliscore;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import jakarta.inject.Inject;
import us.poliscore.dynamodb.DdbBuilder;
import us.poliscore.service.LegislatorService;
import us.poliscore.service.storage.DynamoDbPersistenceService;
import us.poliscore.service.storage.MemoryPersistenceService;

//@QuarkusTest
public class EntrypointTest {

	@Inject
	private LegislatorService legService;
	
	@Inject
	private MemoryPersistenceService memory;
	
	@Inject
	private DynamoDbPersistenceService ddb;
	
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
