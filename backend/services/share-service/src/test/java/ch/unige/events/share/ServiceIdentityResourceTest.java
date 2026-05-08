package ch.unige.events.share;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class ServiceIdentityResourceTest {

    @Test
    void identity_returns200_withServiceName() {
        given()
            .when().get("/api/__service")
            .then()
            .statusCode(200)
            .body("service", equalTo("share-service"))
            .body("status", equalTo("scaffold"));
    }
}
