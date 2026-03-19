package ch.unige.events.resource;

import ch.unige.events.service.UserServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MeResourceTest {

    @Inject
    UserServiceMock userServiceMock;

    @BeforeEach
    void setUp() {
        userServiceMock.reset();
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void testMeAuthenticatedCreatesProfile() {
        given()
                .when().get("/users/me")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", notNullValue())
                .body("auth0_id", equalTo("auth0|alice"))
                .body("email", equalTo("auth0|alice@example.com"))
                .body("is_profile_public", equalTo(false))
                .body("is_admin", equalTo(false))
                .body("created_at", notNullValue());
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void testMeAuthenticatedReturnsExistingProfile() {
        String firstId = given()
            .when().get("/users/me")
            .then()
            .statusCode(200)
            .extract()
            .path("id");

        given()
            .when().get("/users/me")
            .then()
            .statusCode(200)
            .body("id", equalTo(firstId));
    }

    @Test
    void testMeUnauthenticated() {
        given()
                .when().get("/users/me")
                .then()
                .statusCode(401);
    }
}
