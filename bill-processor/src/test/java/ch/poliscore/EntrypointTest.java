package ch.poliscore;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

//@QuarkusTest
public class EntrypointTest {

//	@Test
    public void testSmallBill() throws Exception {
        // you test your lambdas by invoking on http://localhost:8081
        // this works in dev mode too
        
        given()
        		.queryParam("url", TestUtils.C118HR393)
                .get("/processBill")
                .then()
                .statusCode(200);
//                .body(containsString("Hello Stu"));
    }

}
