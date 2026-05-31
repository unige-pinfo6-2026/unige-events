package ch.unige.events.user.follow.resource;

import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.follow.entity.Follow;
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
 * REST tests for {@link FollowResource} and {@link FollowRequestResource}.
 * Fixtures are persisted via {@link TestFixtures} (commits) since
 * REST-Assured requests run in a separate transaction.
 */
@QuarkusTest
class FollowResourceTest {

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
    @TestSecurity(user = "auth0|fr-follow-a")
    void postFollow_publicTarget_returns201Accepted() {
        fixtures.persistUser("auth0|fr-follow-a", "fr-follow-a@example.com", true);
        User b = fixtures.persistUser("auth0|fr-follow-b", "fr-follow-b@example.com", true);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-follow-a"));

        given()
            .when().post("/users/" + b.id + "/follow")
            .then()
            .statusCode(201)
            .body("status", equalTo(FollowStatus.ACCEPTED.name()));
    }

    @Test
    @TestSecurity(user = "auth0|fr-follow-priv-a")
    void postFollow_privateTarget_returns201Pending() {
        fixtures.persistUser("auth0|fr-follow-priv-a", "fr-follow-priv-a@example.com", true);
        User b = fixtures.persistUser("auth0|fr-follow-priv-b", "fr-follow-priv-b@example.com", false);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-follow-priv-a"));

        given()
            .when().post("/users/" + b.id + "/follow")
            .then()
            .statusCode(201)
            .body("status", equalTo(FollowStatus.PENDING.name()));
    }

    @Test
    @TestSecurity(user = "auth0|fr-self-a")
    void postFollow_selfTarget_returns422() {
        User a = fixtures.persistUser("auth0|fr-self-a", "fr-self-a@example.com", true);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-self-a"));

        given()
            .when().post("/users/" + a.id + "/follow")
            .then()
            .statusCode(422);
    }

    @Test
    @TestSecurity(user = "auth0|fr-dup-a")
    void postFollow_duplicate_returns409() {
        fixtures.persistUser("auth0|fr-dup-a", "fr-dup-a@example.com", true);
        User b = fixtures.persistUser("auth0|fr-dup-b", "fr-dup-b@example.com", true);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-dup-a"));

        given().when().post("/users/" + b.id + "/follow").then().statusCode(201);
        given().when().post("/users/" + b.id + "/follow").then().statusCode(409);
    }

    @Test
    @TestSecurity(user = "auth0|fr-uf-a")
    void deleteFollow_existing_returns204() {
        User a = fixtures.persistUser("auth0|fr-uf-a", "fr-uf-a@example.com", true);
        User b = fixtures.persistUser("auth0|fr-uf-b", "fr-uf-b@example.com", true);
        fixtures.persistFollow(a.id, b.id, FollowStatus.ACCEPTED);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-uf-a"));

        given()
            .when().delete("/users/" + b.id + "/follow")
            .then()
            .statusCode(204);
    }

    // ── DELETE /me/followers/{followerId} (remove a follower) ─────────────

    @Test
    @TestSecurity(user = "auth0|fr-rf-tgt")
    void removeFollower_existingRow_returns204AndDropsFromFollowers() {
        User follower = fixtures.persistUser("auth0|fr-rf-follower", "fr-rf-follower@example.com", true);
        User target = fixtures.persistUser("auth0|fr-rf-tgt", "fr-rf-tgt@example.com", true);
        fixtures.persistFollow(follower.id, target.id, FollowStatus.ACCEPTED);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-rf-tgt"));

        given()
            .when().delete("/users/me/followers/" + follower.id)
            .then()
            .statusCode(204);

        // The dropped follower no longer appears in the target's own list.
        given()
            .when().get("/users/" + target.id + "/followers")
            .then()
            .statusCode(200)
            .body("$.size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = "auth0|fr-rf-no-tgt")
    void removeFollower_noRow_isIdempotent204() {
        fixtures.persistUser("auth0|fr-rf-no-tgt", "fr-rf-no-tgt@example.com", true);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-rf-no-tgt"));

        given()
            .when().delete("/users/me/followers/" + UUID.randomUUID())
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|fr-followers-caller")
    void getFollowers_publicTarget_returns200() {
        fixtures.persistUser("auth0|fr-followers-caller", "fr-followers-caller@example.com", true);
        User target = fixtures.persistUser("auth0|fr-followers-tgt", "fr-followers-tgt@example.com", true);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-followers-caller"));

        given()
            .when().get("/users/" + target.id + "/followers")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|fr-followers-other")
    void getFollowers_privateNonOwner_returns404() {
        fixtures.persistUser("auth0|fr-followers-other", "fr-followers-other@example.com", true);
        User target = fixtures.persistUser("auth0|fr-followers-priv-tgt", "fr-followers-priv-tgt@example.com", false);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-followers-other"));

        given()
            .when().get("/users/" + target.id + "/followers")
            .then()
            .statusCode(404);
    }

    @Test
    @TestSecurity(user = "auth0|fr-following-caller")
    void getFollowing_publicTarget_returns200() {
        fixtures.persistUser("auth0|fr-following-caller", "fr-following-caller@example.com", true);
        User target = fixtures.persistUser("auth0|fr-following-tgt", "fr-following-tgt@example.com", true);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-following-caller"));

        given()
            .when().get("/users/" + target.id + "/following")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|fr-pending")
    void getFollowRequests_returns200() {
        fixtures.persistUser("auth0|fr-pending", "fr-pending@example.com", false);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-pending"));

        given()
            .when().get("/users/me/follow-requests")
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|fr-acc-tgt")
    void acceptFollowRequest_byTarget_returns200() {
        User a = fixtures.persistUser("auth0|fr-acc-follower", "fr-acc-follower@example.com", true);
        User b = fixtures.persistUser("auth0|fr-acc-tgt", "fr-acc-tgt@example.com", false);
        Follow pending = fixtures.persistFollow(a.id, b.id, FollowStatus.PENDING);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-acc-tgt"));

        given()
            .when().patch("/follow-requests/" + pending.id + "/accept")
            .then()
            .statusCode(200)
            .body("status", equalTo(FollowStatus.ACCEPTED.name()));
    }

    @Test
    @TestSecurity(user = "auth0|fr-rej-tgt")
    void rejectFollowRequest_byTarget_returns204() {
        User a = fixtures.persistUser("auth0|fr-rej-follower", "fr-rej-follower@example.com", true);
        User b = fixtures.persistUser("auth0|fr-rej-tgt", "fr-rej-tgt@example.com", false);
        Follow pending = fixtures.persistFollow(a.id, b.id, FollowStatus.PENDING);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-rej-tgt"));

        given()
            .when().patch("/follow-requests/" + pending.id + "/reject")
            .then()
            .statusCode(204);
    }

    @Test
    void postFollow_unauthenticated_returns401() {
        given()
            .when().post("/users/" + UUID.randomUUID() + "/follow")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|fr-getf-caller")
    void getFollowers_listIncludesPrivateFollower_returnsAnonymousProjection() {
        User caller = fixtures.persistUser("auth0|fr-getf-caller", "fr-getf-caller@example.com", true);
        User target = fixtures.persistUser("auth0|fr-getf-tgt", "fr-getf-tgt@example.com", true);
        User priv = fixtures.persistUser("auth0|fr-getf-priv", "fr-getf-priv@example.com", false);
        User pub = fixtures.persistUser("auth0|fr-getf-pub", "fr-getf-pub@example.com", true);
        fixtures.persistFollow(priv.id, target.id, FollowStatus.ACCEPTED);
        fixtures.persistFollow(pub.id, target.id, FollowStatus.ACCEPTED);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-getf-caller"));

        given()
            .when().get("/users/" + target.id + "/followers")
            .then()
            .statusCode(200)
            .body("$.size()", equalTo(2));
    }

    @Test
    @TestSecurity(user = "auth0|fr-self-proj-caller")
    void getFollowers_listIncludesSelfPrivateRow_returnsFullSelfProjection() {
        // Branches L136/L137 of projectListItem: a PRIVATE caller appears in
        // a list as their own row → `profilePublic || isSelf` short-circuits
        // on isSelf=true, so the self row is projected via the FULL
        // UserPublicResponse.from(user) instead of fromAnonymous. The caller
        // (private) follows a public target, so the caller shows up in the
        // target's followers list as themselves.
        User caller = fixtures.persistUser("auth0|fr-self-proj-caller", "fr-self-proj-caller@example.com", false);
        User target = fixtures.persistUser("auth0|fr-self-proj-tgt", "fr-self-proj-tgt@example.com", true);
        fixtures.persistFollow(caller.id, target.id, FollowStatus.ACCEPTED);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-self-proj-caller"));

        given()
            .when().get("/users/" + target.id + "/followers")
            .then()
            .statusCode(200)
            .body("$.size()", equalTo(1))
            .body("[0].id", equalTo(caller.id.toString()))
            .body("[0].username", equalTo(caller.username))
            .body("[0].profilePublic", equalTo(false));
    }

    @Test
    @TestSecurity(user = "auth0|fr-getfwg-caller")
    void getFollowing_listIncludesPrivateFollowed_returnsAnonymousProjection() {
        User caller = fixtures.persistUser("auth0|fr-getfwg-caller", "fr-getfwg-caller@example.com", true);
        User priv = fixtures.persistUser("auth0|fr-getfwg-priv", "fr-getfwg-priv@example.com", false);
        fixtures.persistFollow(caller.id, priv.id, FollowStatus.ACCEPTED);
        JwtTestContext.set(JwtTestHelper.jwtFor("auth0|fr-getfwg-caller"));

        given()
            .when().get("/users/" + caller.id + "/following")
            .then()
            .statusCode(200)
            .body("$.size()", equalTo(1));
    }
}
