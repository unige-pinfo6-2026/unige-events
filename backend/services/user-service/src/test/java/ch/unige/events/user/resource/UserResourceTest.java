package ch.unige.events.user.resource;

import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.shared.storage.FileStorageService;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.test.JwtTestContext;
import ch.unige.events.user.test.JwtTestHelper;
import ch.unige.events.user.test.TestFixtures;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * REST-layer tests for {@link UserResource}. Fixtures are persisted via
 * {@link TestFixtures} (commits in its own transaction) since
 * REST-Assured requests run in a separate transaction.
 */
@QuarkusTest
class UserResourceTest {

    @Inject TestFixtures fixtures;

    @InjectMock FileStorageService fileStorageService;

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
    @TestSecurity(user = "auth0|ur-gbun-self")
    void getByUsername_authedSelf_publicProfile_returnsFullProjection() {
        // Authenticated, non-restricted (own public profile) → the resource
        // serialises the FULL projection (followerCount/followingCount/followStatus),
        // not the anonymous one.
        User user = fixtures.persistUser("auth0|ur-gbun-self", "ur-gbun-self@example.com", true);
        JwtTestContext.set(JwtTestHelper.jwtWithEmail(
                "auth0|ur-gbun-self", "ur-gbun-self@example.com"));

        given()
            .when().get("/users/by-username/" + user.username)
            .then()
            .statusCode(200)
            .body("id", equalTo(user.id.toString()))
            .body("followerCount", equalTo(0))
            .body("followingCount", equalTo(0));
    }

    @Test
    void getProfile_publicUser_anonymousCaller_returns200() {
        User user = fixtures.persistUser("auth0|ur-pub", "ur-pub@example.com", true);

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200)
            .body("id", equalTo(user.id.toString()))
            .body("profilePublic", equalTo(true));
    }

    @Test
    void getProfile_privateUser_anonymousCaller_returnsRestrictedProjection() {
        // SCRUM-169 revision — private profile + non-self non-admin caller
        // (here anonymous) used to 404 ; we now return the stripped
        // anonymous projection so cross-service enrichment keeps the
        // public-facing identifier visible (id, username, displayName,
        // avatarUrl). The private fields stay null + counters stay zero ;
        // profilePublic is forced to false so the frontend can gate the
        // "this profile is private" placeholder without leaking other fields.
        User user = fixtures.persistUser("auth0|ur-priv-anon", "ur-priv-anon@example.com", false);

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200)
            .body("id", equalTo(user.id.toString()))
            .body("username", equalTo(user.username))
            .body("bio", nullValue())
            .body("faculty", nullValue())
            .body("studyLevel", nullValue())
            .body("interests", nullValue())
            .body("bannerUrl", nullValue())
            .body("profilePublic", equalTo(false))
            .body("followerCount", equalTo(0))
            .body("followingCount", equalTo(0))
            .body("followStatus", nullValue());
    }

    @Test
    @TestSecurity(user = "auth0|ur-priv-other-caller")
    void getProfile_privateUser_authedNonSelfNonAdmin_returnsRestrictedProjection() {
        // Covers the `anonymous || view.restricted()` partial where
        // anonymous=false (authenticated) but restricted=true: a non-self
        // non-admin caller hitting a PRIVATE profile gets the stripped
        // anonymous projection, not a 404.
        User target = fixtures.persistUser("auth0|ur-priv-other-target", "ur-priv-other-target@example.com", false);
        fixtures.persistUser("auth0|ur-priv-other-caller", "ur-priv-other-caller@example.com", true);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-priv-other-caller"));

        given()
            .when().get("/users/" + target.id)
            .then()
            .statusCode(200)
            .body("id", equalTo(target.id.toString()))
            .body("username", equalTo(target.username))
            .body("bio", nullValue())
            .body("faculty", nullValue())
            .body("bannerUrl", nullValue())
            .body("profilePublic", equalTo(false))
            .body("followerCount", equalTo(0))
            .body("followingCount", equalTo(0))
            .body("followStatus", nullValue());
    }

    @Test
    void getProfile_publicUser_anonymous_returnsFullProjectionWithBannerBioAndCounts() {
        // Anonymous viewer of a PUBLIC profile now receives the FULL projection:
        // banner, bio, faculty AND real follower counts — a public profile is
        // meant to be visible to everyone (revises the over-broad anonymous strip).
        User user = fixtures.persistUser("auth0|ur-pub-full", "ur-pub-full@example.com", true);
        fixtures.setProfileDetails(user.id, "https://cdn/banners/pub.png", "ma bio publique", Faculty.SCIENCES);
        User follower = fixtures.persistUser("auth0|ur-pub-full-follower", "ur-pub-full-follower@example.com", true);
        fixtures.persistFollow(follower.id, user.id, FollowStatus.ACCEPTED);

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200)
            .body("profilePublic", equalTo(true))
            .body("bannerUrl", equalTo("https://cdn/banners/pub.png"))
            .body("bio", equalTo("ma bio publique"))
            .body("faculty", equalTo("SCIENCES"))
            .body("followerCount", equalTo(1))
            .body("followingCount", equalTo(0));
    }

    @Test
    void getProfile_privateUser_anonymous_lockedProjectionExposesBannerAndCountsButStripsBio() {
        // Locked projection: banner + counts are now exposed (carte « compte
        // privé »), but bio / faculty / studyLevel / interests stay stripped.
        User user = fixtures.persistUser("auth0|ur-priv-locked", "ur-priv-locked@example.com", false);
        fixtures.setProfileDetails(user.id, "https://cdn/banners/priv.png", "bio secrète", Faculty.SCIENCES);
        User follower = fixtures.persistUser("auth0|ur-priv-locked-follower", "ur-priv-locked-follower@example.com", true);
        fixtures.persistFollow(follower.id, user.id, FollowStatus.ACCEPTED);

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200)
            .body("profilePublic", equalTo(false))
            .body("bannerUrl", equalTo("https://cdn/banners/priv.png"))
            .body("followerCount", equalTo(1))
            .body("bio", nullValue())
            .body("faculty", nullValue());
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
    @TestSecurity(user = "auth0|ur-img-happy")
    void uploadImage_withFile_returns200_setsAvatarUrl() {
        // Happy path: the resource's file != null branch + delegation to the
        // service. FileStorageService is mocked so no real S3 round-trip
        // happens (no Docker / S3 in compile-only CI).
        fixtures.persistUser("auth0|ur-img-happy", "ur-img-happy@example.com", false);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-img-happy"));
        when(fileStorageService.saveImage(any(FileUpload.class), anyString(), anyLong(), any()))
                .thenReturn("https://cdn/users/avatars/happy.png");

        given()
            .multiPart("file", "avatar.jpg", new byte[]{1, 2, 3}, "image/jpeg")
            .when().post("/users/me/image")
            .then()
            .statusCode(200)
            .body("avatarUrl", equalTo("https://cdn/users/avatars/happy.png"));
    }

    @Test
    @TestSecurity(user = "auth0|ur-banner-happy")
    void uploadBanner_withFile_returns200_setsBannerUrl() {
        fixtures.persistUser("auth0|ur-banner-happy", "ur-banner-happy@example.com", false);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-banner-happy"));
        when(fileStorageService.saveImage(any(FileUpload.class), anyString(), anyLong(), any()))
                .thenReturn("https://cdn/users/banners/happy.png");

        given()
            .multiPart("file", "banner.jpg", new byte[]{4, 5, 6}, "image/jpeg")
            .when().post("/users/me/banner")
            .then()
            .statusCode(200)
            .body("bannerUrl", equalTo("https://cdn/users/banners/happy.png"));
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
            .body("username", equalTo("public.alice"))
            .body("profilePublic", equalTo(true));
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
    void getByUsername_privateProfile_anonymous_returnsRestrictedProjection() {
        // SCRUM-169 revision — see getProfile_privateUser_anonymousCaller_
        // returnsRestrictedProjection for the rationale.
        fixtures.persistUser("auth0|ur-byuname-priv-anon", "priv-anon@example.com", false,
                "priv.alice");

        given()
            .when().get("/users/by-username/priv.alice")
            .then()
            .statusCode(200)
            .body("username", equalTo("priv.alice"))
            .body("bio", nullValue())
            .body("faculty", nullValue())
            .body("bannerUrl", nullValue())
            .body("profilePublic", equalTo(false))
            .body("followerCount", equalTo(0))
            .body("followingCount", equalTo(0))
            .body("followStatus", nullValue());
    }

    @Test
    @TestSecurity(user = "auth0|ur-byuname-other")
    void getByUsername_privateProfile_otherUser_returnsRestrictedProjection() {
        // SCRUM-169 revision — authenticated non-self non-admin caller
        // accessing a private profile now sees the stripped projection
        // instead of a 404. profilePublic is forced to false so the
        // frontend renders the "private profile" placeholder.
        fixtures.persistUser("auth0|ur-byuname-priv-target", "target@example.com", false,
                "priv.target");
        fixtures.persistUser("auth0|ur-byuname-other", "caller@example.com", false,
                "caller.handle");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-byuname-other"));

        given()
            .when().get("/users/by-username/priv.target")
            .then()
            .statusCode(200)
            .body("username", equalTo("priv.target"))
            .body("bio", nullValue())
            .body("faculty", nullValue())
            .body("bannerUrl", nullValue())
            .body("profilePublic", equalTo(false))
            .body("followerCount", equalTo(0))
            .body("followingCount", equalTo(0))
            .body("followStatus", nullValue());
    }

    @Test
    @TestSecurity(user = "auth0|ur-byuname-admin", roles = {"ADMIN"})
    void getByUsername_privateProfile_adminCaller_returnsFullProjection() {
        // Covers the `!anonymous && hasRole(ADMIN)` admin path of
        // getByUsername: an ADMIN caller bypasses the anti-oracle, so the
        // service returns a non-restricted view and the resource serialises
        // the FULL projection (followerCount / followingCount present).
        fixtures.persistUser("auth0|ur-byuname-admin-tgt", "admin-tgt@example.com", false,
                "admin.priv.target");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-byuname-admin"));

        given()
            .when().get("/users/by-username/admin.priv.target")
            .then()
            .statusCode(200)
            .body("username", equalTo("admin.priv.target"))
            .body("profilePublic", equalTo(false))
            .body("followerCount", equalTo(0))
            .body("followingCount", equalTo(0));
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

    // ─────────────────────────────────────────────────────────────────────────
    // SCRUM-137 polish — GET /users/search (username autocomplete)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void searchByUsername_anonymous_returns401() {
        // @Authenticated guard — anti-enumeration.
        given()
            .when().get("/users/search?q=al")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|ur-search-caller")
    void searchByUsername_prefixMatches_returns200_sortedAscending() {
        fixtures.persistUser("auth0|ur-search-caller", "caller@example.com", true, "search.caller");
        fixtures.persistUser("auth0|ur-search-1",  "a1@example.com", true,  "alpha.one");
        fixtures.persistUser("auth0|ur-search-2",  "a2@example.com", false, "alpha.two");
        fixtures.persistUser("auth0|ur-search-3",  "z@example.com",  true,  "zeta.three");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-search-caller"));

        given()
            .when().get("/users/search?q=alpha")
            .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("username", contains("alpha.one", "alpha.two"));
    }

    @Test
    @TestSecurity(user = "auth0|ur-search-caller-2")
    void searchByUsername_excludesCaller() {
        fixtures.persistUser("auth0|ur-search-caller-2", "me@example.com", true, "alpha.caller");
        fixtures.persistUser("auth0|ur-search-other",    "other@example.com", true, "alpha.other");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-search-caller-2"));

        given()
            .when().get("/users/search?q=alpha")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("username", contains("alpha.other"));
    }

    @Test
    @TestSecurity(user = "auth0|ur-search-case-caller")
    void searchByUsername_caseInsensitive_findsLowercased() {
        // Usernames are stored lowercase ; the resource lowers q before the
        // prefix scan so "NEX" still matches "nexiumito".
        fixtures.persistUser("auth0|ur-search-case-caller", "c@example.com", true, "case.caller");
        fixtures.persistUser("auth0|ur-search-case-target", "n@example.com", true, "nexiumito");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-search-case-caller"));

        given()
            .when().get("/users/search?q=NEX")
            .then()
            .statusCode(200)
            .body("username", contains("nexiumito"));
    }

    @Test
    @TestSecurity(user = "auth0|ur-search-limit-caller")
    void searchByUsername_honoursLimit() {
        fixtures.persistUser("auth0|ur-search-limit-caller", "c@example.com", true, "lim.caller");
        for (int i = 0; i < 5; i++) {
            fixtures.persistUser(
                    "auth0|ur-search-lim-" + i,
                    "lim" + i + "@example.com",
                    true,
                    "limit." + i);
        }
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-search-limit-caller"));

        given()
            .when().get("/users/search?q=limit&limit=3")
            .then()
            .statusCode(200)
            .body("$", hasSize(3))
            .body("username", contains("limit.0", "limit.1", "limit.2"));
    }

    @Test
    @TestSecurity(user = "auth0|ur-search-limit-default-caller")
    void searchByUsername_defaultsLimitTo8() {
        fixtures.persistUser("auth0|ur-search-limit-default-caller", "c@example.com", true, "def.caller");
        for (int i = 0; i < 12; i++) {
            // Two-digit suffix so the lexicographic order matches the numeric one.
            String suffix = i < 10 ? "0" + i : Integer.toString(i);
            fixtures.persistUser(
                    "auth0|ur-search-def-" + suffix,
                    "def" + suffix + "@example.com",
                    true,
                    "def.row" + suffix);
        }
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-search-limit-default-caller"));

        given()
            .when().get("/users/search?q=def.row")
            .then()
            .statusCode(200)
            .body("$", hasSize(8));
    }

    @Test
    @TestSecurity(user = "auth0|ur-search-tooshort-caller")
    void searchByUsername_qTooShort_returns400() {
        fixtures.persistUser("auth0|ur-search-tooshort-caller", "c@example.com", true, "short.caller");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-search-tooshort-caller"));

        given()
            .when().get("/users/search?q=a")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|ur-search-toolong-caller")
    void searchByUsername_qTooLong_returns400() {
        fixtures.persistUser("auth0|ur-search-toolong-caller", "c@example.com", true, "long.caller");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-search-toolong-caller"));

        given()
            .when().get("/users/search?q=" + "a".repeat(31))
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|ur-search-bad-char-caller")
    void searchByUsername_qInvalidChar_returns400() {
        // Belt-and-suspenders: the @Pattern blocks SQL wildcards (%, etc.)
        // even though the entity finder escapes the underscore separately.
        fixtures.persistUser("auth0|ur-search-bad-char-caller", "c@example.com", true, "char.caller");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-search-bad-char-caller"));

        given()
            .when().get("/users/search?q=al%25")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|ur-search-limit-overflow-caller")
    void searchByUsername_limitAbove20_returns400() {
        fixtures.persistUser("auth0|ur-search-limit-overflow-caller", "c@example.com", true, "over.caller");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-search-limit-overflow-caller"));

        given()
            .when().get("/users/search?q=al&limit=21")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|ur-search-limit-zero-caller")
    void searchByUsername_limitZero_returns400() {
        fixtures.persistUser("auth0|ur-search-limit-zero-caller", "c@example.com", true, "zero.caller");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-search-limit-zero-caller"));

        given()
            .when().get("/users/search?q=al&limit=0")
            .then()
            .statusCode(400);
    }

    @Test
    @TestSecurity(user = "auth0|ur-search-priv-caller")
    void searchByUsername_includesPrivateProfilesButStripsPrivateFields() {
        // SCRUM-169 E: username is a public-facing identifier even for private
        // profiles ; the search must surface them so an organizer can invite
        // anyone they know the handle of. But the payload uses the
        // fromAnonymous projection, so bio / faculty / banner stay null.
        fixtures.persistUser("auth0|ur-search-priv-caller", "c@example.com", true, "priv.caller");
        fixtures.persistUser("auth0|ur-search-priv-target", "t@example.com", false, "priv.target");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-search-priv-caller"));

        given()
            .when().get("/users/search?q=priv.t")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].username", equalTo("priv.target"))
            .body("[0].profilePublic", equalTo(false))
            .body("[0].bio", nullValue())
            .body("[0].faculty", nullValue())
            .body("[0].bannerUrl", nullValue());
    }

    @Test
    @TestSecurity(user = "auth0|ur-search-no-match-caller")
    void searchByUsername_noMatches_returnsEmptyArray() {
        fixtures.persistUser("auth0|ur-search-no-match-caller", "c@example.com", true, "nm.caller");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-search-no-match-caller"));

        given()
            .when().get("/users/search?q=zzz.never")
            .then()
            .statusCode(200)
            .body("$", empty());
    }

    @Test
    @TestSecurity(user = "auth0|ur-search-escape-caller")
    void searchByUsername_underscoreWildcardIsEscaped() {
        // The username regex allows '_', which is also the LIKE single-char
        // wildcard. The finder escapes it so 'al_' must match literal "al_"
        // prefixes only, not "ali", "alo", etc.
        fixtures.persistUser("auth0|ur-search-escape-caller", "c@example.com", true, "escape.caller");
        fixtures.persistUser("auth0|ur-search-escape-1", "u@example.com", true, "al_underscore");
        fixtures.persistUser("auth0|ur-search-escape-2", "v@example.com", true, "alice.runner");
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|ur-search-escape-caller"));

        given()
            .when().get("/users/search?q=al_")
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("username", contains("al_underscore"))
            .body("username", not(contains("alice.runner")));
    }

    /** Unused argument suppression for {@link UUID} import retention. */
    @SuppressWarnings("unused")
    private void _keepUuidImport(UUID u) {
        // Used by older tests above ; placeholder so the import survives if those tests are pruned.
    }
}
