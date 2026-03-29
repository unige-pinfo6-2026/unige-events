package ch.unige.events.resource;

import ch.unige.events.service.UserServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.SecurityAttribute;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class UserResourceTest {

    @Inject UserServiceMock userServiceMock;

    @BeforeEach
    void setUp() {
        userServiceMock.reset();
    }

    @Test
    void getProfilePublicSuccess() {
        var user = userServiceMock.seedUser("auth0|public-profile", "public@example.com");
        user.profilePublic = true;

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(user.id.toString()));
    }

    @Test
    void getProfilePrivateForbidden() {
        var user = userServiceMock.seedUser("auth0|private-profile", "private@example.com");

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(403)
            .body("error", equalTo("forbidden"));
    }

    @Test
    void getProfileNotFound() {
        given()
            .when().get("/users/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
    }

    @Test
    @TestSecurity(user = "auth0|alice", attributes = {
        @SecurityAttribute(key = "email", value = "alice@example.com"),
        @SecurityAttribute(key = "name", value = "Alice"),
        @SecurityAttribute(key = "picture", value = "https://cdn.example.com/alice.png")
    })
    void getMeAuthenticatedCreatesProfile() {
        given()
            .when().get("/users/me")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("auth0Id", equalTo("auth0|alice"))
            .body("email", equalTo("alice@example.com"))
            .body("displayName", equalTo("Alice"))
            .body("avatarUrl", equalTo("https://cdn.example.com/alice.png"))
            .body("profilePublic", equalTo(false))
            .body("createdAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMeAuthenticatedReturnsExistingProfile() {
        String firstId = userServiceMock.seedUser("auth0|alice", "alice@example.com").id.toString();

        given()
            .when().get("/users/me")
            .then()
            .statusCode(200)
            .body("id", equalTo(firstId));
    }

    @Test
    @TestSecurity(user = "auth0|bob")
    void getMeMissingEmailClaimReturnsUnauthorized() {
        given()
            .when().get("/users/me")
            .then()
            .statusCode(401)
            .contentType(ContentType.JSON)
            .body("error", equalTo("unauthorized"));
    }

    @Test
    void getMeUnauthenticated() {
        given()
            .when().get("/users/me")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void updateMePartialUpdateSuccess() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "displayName": "Alice",
                  "bio": "Student at UNIGE",
                  "profilePublic": true
                }
                """)
            .when().put("/users/me")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", notNullValue())
            .body("auth0Id", equalTo("auth0|alice"))
            .body("email", equalTo("alice@example.com"))
            .body("displayName", equalTo("Alice"))
            .body("bio", equalTo("Student at UNIGE"))
            .body("profilePublic", equalTo(true));
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
    void updateMeInvalidBodyValidation() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");

        given()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "avatarUrl": "ftp://invalid.example.com/avatar.png"
                }
                """)
            .when().put("/users/me")
            .then()
            .statusCode(400)
            .body("error", equalTo("validation_error"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void updateMeForbidden() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");
        userServiceMock.forceForbiddenOnUpdate = true;

        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().put("/users/me")
            .then()
            .statusCode(403)
            .body("error", equalTo("forbidden"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void updateMeConflict() {
        userServiceMock.seedUser("auth0|alice", "alice@example.com");
        userServiceMock.forceConflictOnUpdate = true;

        given()
            .contentType(ContentType.JSON)
            .body("{}")
            .when().put("/users/me")
            .then()
            .statusCode(409)
            .body("error", equalTo("conflict"));
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
