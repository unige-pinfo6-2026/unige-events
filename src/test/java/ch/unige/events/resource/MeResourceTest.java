package ch.unige.events.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MeResourceTest {

    @Test
    @TestSecurity(user = "alice")
    void testMeAuthenticated() {
        given()
                .when().get("/me")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("sub", notNullValue());
    }

    @Test
    void testMeUnauthenticated() {
        given()
                .when().get("/me")
                .then()
                .statusCode(401);
    }
}
