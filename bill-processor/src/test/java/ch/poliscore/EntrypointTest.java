package ch.poliscore;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import ch.poliscore.service.MockAIService;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class EntrypointTest {

	@Test
    public void testSmallBill() throws Exception {
        // you test your lambdas by invoking on http://localhost:8081
        // this works in dev mode too
    	
    	MockAIService.setResponse(IOUtils.toString(EntrypointTest.class.getResourceAsStream("/ai/hr806.txt"), "UTF-8"));
        
        given()
        		.queryParam("url", "https://www.congress.gov/115/bills/hr806/BILLS-115hr806rfs.xml")
                .get("/processBill")
                .then()
                .statusCode(200)
                .body(containsString("Hello Stu"));
    }

}
