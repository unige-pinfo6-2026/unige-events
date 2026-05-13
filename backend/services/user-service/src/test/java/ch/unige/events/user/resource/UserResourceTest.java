package ch.unige.events.user.resource;

import ch.unige.events.user.entity.User;
import ch.unige.events.user.test.JwtTestContext;
import ch.unige.events.user.test.JwtTestHelper;
import ch.unige.events.user.test.TestFixtures;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * REST-layer tests for {@link UserResource}. Fixtures are persisted via
 * {@link TestFixtures} (commits in its own transaction) since
 * REST-Assured requests run in a separate transaction.
 */
@QuarkusTest
class UserResourceTest {

    @Inject TestFixtures fixtures;

    @BeforeEach
    void clearJwt() {
        JwtTestContext.clear();
        fixtures.truncateAll();
    }

    @AfterEach
    void resetJwt() {
        JwtTestContext.clear();
        fixtures.truncateAll();
    }

    @Test
    @TestSecurity(user = "auth0|ur-me-existing")
    void getMe_existingUser_returns200() {
        User user = fixtures.persistUser("auth0|ur-me-existing", "ur-me-existing@example.com", false);
        JwtTestContext.set(JwtTestHelper.jwtWithEmail(
                "auth0|ur-me-existing", "ur-me-existing@example.com"));

        given()
            .when().get("/users/me")
            .then()
            .statusCode(200)
            .body("auth0Id", equalTo("auth0|ur-me-existing"))
            .body("id", equalTo(user.id.toString()));
    }

    @Test
    @TestSecurity(user = "auth0|ur-me-create")
    void getMe_newUser_isCreatedFromJwtClaims() {
        JwtTestContext.set(JwtTestHelper.jwtWithEmail(
                "auth0|ur-me-create", "ur-me-create@example.com"));

        given()
            .when().get("/users/me")
            .then()
            .statusCode(200)
            .body("auth0Id", equalTo("auth0|ur-me-create"))
            .body("email", equalTo("ur-me-create@example.com"));
    }

    @Test
    void getMe_noAuth_returns401() {
        given()
            .when().get("/users/me")
            .then()
            .statusCode(401);
    }

    @Test
    void getProfile_publicUser_anonymousCaller_returns200() {
        User user = fixtures.persistUser("auth0|ur-pub", "ur-pub@example.com", true);

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200)
            .body("id", equalTo(user.id.toString()));
    }

    @Test
    void getProfile_privateUser_anonymousCaller_returns404() {
        User user = fixtures.persistUser("auth0|ur-priv-anon", "ur-priv-anon@example.com", false);

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(404);
    }

    @Test
    void getProfile_unknownId_returns404() {
        given()
            .when().get("/users/" + UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|ur-priv-self")
    void getProfile_privateUser_selfCaller_returns200() {
        User user = fixtures.persistUser("auth0|ur-priv-self", "ur-priv-self@example.com", false);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-priv-self"));

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|ur-priv-admin", roles = {"ADMIN"})
    void getProfile_privateUser_adminCaller_bypassesAntiOracle() {
        User user = fixtures.persistUser("auth0|ur-priv-tgt", "ur-priv-tgt@example.com", false);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-priv-admin"));

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|ur-upd")
    void putMe_updatesProfile_returns200() {
        fixtures.persistUser("auth0|ur-upd", "ur-upd@example.com", false);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-upd"));

        given()
            .contentType("application/json")
            .body("{\"displayName\":\"Updated\",\"profilePublic\":true}")
            .when().put("/users/me")
            .then()
            .statusCode(200)
            .body("displayName", equalTo("Updated"))
            .body("profilePublic", equalTo(true));
    }

    @Test
    @TestSecurity(user = "auth0|ur-upd-validation")
    void putMe_invalidUrl_returns400() {
        // avatarUrl is bound to a @Pattern ^(https?://).+$ regex.
        given()
            .contentType("application/json")
            .body("{\"avatarUrl\":\"not-a-url\"}")
            .when().put("/users/me")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|ur-img-no-file")
    void uploadImage_noFile_returns400() {
        fixtures.persistUser("auth0|ur-img-no-file", "ur-img-no-file@example.com", false);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-img-no-file"));

        given()
            .multiPart("ignored", "")
            .when().post("/users/me/image")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|ur-banner-no-file")
    void uploadBanner_noFile_returns400() {
        fixtures.persistUser("auth0|ur-banner-no-file", "ur-banner-no-file@example.com", false);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-banner-no-file"));

        given()
            .multiPart("ignored", "")
            .when().post("/users/me/banner")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|ur-del-img")
    void deleteImage_existingUser_returns200() {
        fixtures.persistUser("auth0|ur-del-img", "ur-del-img@example.com", false);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-del-img"));

        given()
            .when().delete("/users/me/image")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|ur-del-banner")
    void deleteBanner_existingUser_returns200() {
        fixtures.persistUser("auth0|ur-del-banner", "ur-del-banner@example.com", false);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-del-banner"));

        given()
            .when().delete("/users/me/banner")
            .then()
            .statusCode(200);
    }
}
