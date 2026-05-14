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

    // ─── SCRUM-169 — username endpoints ──────────────────────────────────

    @Test
    @TestSecurity(user = "auth0|ur-uname-happy")
    void patchUsername_happyPath_returns200WithUpdatedUsername() {
        fixtures.persistUser("auth0|ur-uname-happy", "ur-uname-happy@example.com", false,
                "old.username");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-uname-happy"));

        given()
            .contentType("application/json")
            .body("{\"username\":\"new.username\"}")
            .when().patch("/users/me/username")
            .then()
            .statusCode(200)
            .body("username", equalTo("new.username"));
    }

    @Test
    @TestSecurity(user = "auth0|ur-uname-conflict")
    void patchUsername_alreadyTakenByOther_returns409() {
        fixtures.persistUser("auth0|ur-uname-conflict-other", "other@example.com", false,
                "taken.handle");
        fixtures.persistUser("auth0|ur-uname-conflict", "self@example.com", false,
                "self.handle");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-uname-conflict"));

        given()
            .contentType("application/json")
            .body("{\"username\":\"taken.handle\"}")
            .when().patch("/users/me/username")
            .then()
            .statusCode(409)
            .body("error", equalTo("username_taken"));
    }

    @Test
    @TestSecurity(user = "auth0|ur-uname-invalid")
    void patchUsername_invalidPattern_returns400() {
        fixtures.persistUser("auth0|ur-uname-invalid", "ur-inv@example.com", false,
                "ok.handle");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-uname-invalid"));

        given()
            .contentType("application/json")
            .body("{\"username\":\"Jean Dupont\"}") // uppercase + space → invalid
            .when().patch("/users/me/username")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|ur-uname-reserved")
    void patchUsername_reservedWord_returns400_usernameReserved() {
        fixtures.persistUser("auth0|ur-uname-reserved", "ur-res@example.com", false,
                "ok.handle2");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-uname-reserved"));

        given()
            .contentType("application/json")
            .body("{\"username\":\"admin\"}")
            .when().patch("/users/me/username")
            .then()
            .statusCode(400)
            .body("error", equalTo("username_reserved"));
    }

    @Test
    void patchUsername_unauthenticated_returns401() {
        given()
            .contentType("application/json")
            .body("{\"username\":\"any.thing\"}")
            .when().patch("/users/me/username")
            .then()
            .statusCode(401);
    }

    @Test
    void getByUsername_publicProfile_anonymous_returns200() {
        fixtures.persistUser("auth0|ur-byuname-pub", "ur-byuname-pub@example.com", true,
                "public.alice");

        given()
            .when().get("/users/by-username/public.alice")
            .then()
            .statusCode(200)
            .body("username", equalTo("public.alice"));
    }

    @Test
    void getByUsername_caseInsensitive_findsLowercased() {
        fixtures.persistUser("auth0|ur-byuname-case", "case@example.com", true,
                "case.alice");

        given()
            .when().get("/users/by-username/Case.Alice")
            .then()
            .statusCode(200)
            .body("username", equalTo("case.alice"));
    }

    @Test
    void getByUsername_privateProfile_anonymous_returns404() {
        fixtures.persistUser("auth0|ur-byuname-priv-anon", "priv-anon@example.com", false,
                "priv.alice");

        given()
            .when().get("/users/by-username/priv.alice")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|ur-byuname-other")
    void getByUsername_privateProfile_otherUser_returns404() {
        fixtures.persistUser("auth0|ur-byuname-priv-target", "target@example.com", false,
                "priv.target");
        fixtures.persistUser("auth0|ur-byuname-other", "caller@example.com", false,
                "caller.handle");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-byuname-other"));

        given()
            .when().get("/users/by-username/priv.target")
            .then()
            .statusCode(404);
    }

    @Test
    void getByUsername_notFound_returns404() {
        given()
            .when().get("/users/by-username/no.such.user")
            .then()
            .statusCode(404);
    }

    @Test
    void getByUsername_anonymous_payloadIncludesUsername() {
        // SCRUM-169 Décision E — username MUST be exposed even when the
        // payload is otherwise stripped for an anonymous caller.
        fixtures.persistUser("auth0|ur-byuname-anon", "anon@example.com", true,
                "anon.target");

        given()
            .when().get("/users/by-username/anon.target")
            .then()
            .statusCode(200)
            .body("username", equalTo("anon.target"));
    }

    @Test
    void headByUsername_taken_returns200() {
        fixtures.persistUser("auth0|ur-head-taken", "head-t@example.com", true,
                "head.taken");

        given()
            .when().head("/users/by-username/head.taken")
            .then()
            .statusCode(200);
    }

    @Test
    void headByUsername_available_returns404() {
        // Inversion sémantique : 404 = libre.
        given()
            .when().head("/users/by-username/never.exists.xyz")
            .then()
            .statusCode(404);
    }

    @Test
    void headByUsername_caseInsensitive_findsLowercased() {
        fixtures.persistUser("auth0|ur-head-case", "head-c@example.com", true,
                "head.case");

        given()
            .when().head("/users/by-username/Head.Case")
            .then()
            .statusCode(200);
    }

    /** Unused argument suppression for {@link UUID} import retention. */
    @SuppressWarnings("unused")
    private void _keepUuidImport(UUID u) {
        // Used by older tests above ; placeholder so the import survives if those tests are pruned.
    }
}
