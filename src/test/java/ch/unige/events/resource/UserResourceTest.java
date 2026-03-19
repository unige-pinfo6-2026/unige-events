package ch.unige.events.resource;

import ch.unige.events.service.UserServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class UserResourceTest {

    @Inject
    UserServiceMock userServiceMock;

    @BeforeEach
    void setUp() {
        userServiceMock.reset();
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void updateMePartialUpdateSuccess() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "display_name": "Alice",
                  "bio": "Student at UNIGE",
                  "is_profile_public": true
                }
                """)
            .when().put("/users/me")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("auth0_id", equalTo("auth0|alice"))
            .body("email", equalTo("alice@example.com"))
            .body("display_name", equalTo("Alice"))
            .body("bio", equalTo("Student at UNIGE"))
            .body("is_profile_public", equalTo(true));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void updateMeNotFound() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().put("/users/me")
            .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void updateMeInvalidBodyUnknownField() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "email": "new@example.com"
                }
                """)
            .when().put("/users/me")
            .then()
                        .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void updateMeForbidden() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");
        userServiceMock.setForceForbiddenOnUpdate(true);

        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().put("/users/me")
            .then()
            .statusCode(403)
            .body("error", equalTo("forbidden"));
    }

    @Test
    void updateMeUnauthenticated() {
        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().put("/users/me")
            .then()
            .statusCode(401);
    }
}
