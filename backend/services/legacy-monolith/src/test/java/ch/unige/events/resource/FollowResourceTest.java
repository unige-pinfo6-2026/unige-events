package ch.unige.events.resource;

import ch.unige.events.entity.Follow;
import ch.unige.events.entity.FollowStatus;
import ch.unige.events.entity.User;
import ch.unige.events.service.FollowServiceMock;
import ch.unige.events.service.UserServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class FollowResourceTest {

    @Inject FollowServiceMock followServiceMock;
    @Inject UserServiceMock userServiceMock;

    @BeforeEach
    void setUp() {
        followServiceMock.reset();
        userServiceMock.reset();
    }

    // =========================================================
    // POST /users/{id}/follow
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void follow_publicProfile_returnsAccepted_201() {
        User target = userServiceMock.seedUser("auth0|target-pub", "target-pub@example.com");
        target.profilePublic = true;
        FollowServiceMock.nextCreateStatus = FollowStatus.ACCEPTED;

        given()
            .when().post("/users/{id}/follow", target.id)
            .then()
            .statusCode(201)
            .body("status", is("ACCEPTED"))
            .body("followedId", is(target.id.toString()))
            .body("id", notNullValue())
            .body("createdAt", notNullValue());
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void follow_privateProfile_returnsPending_201() {
        User target = userServiceMock.seedUser("auth0|target-priv", "target-priv@example.com");
        target.profilePublic = false;
        FollowServiceMock.nextCreateStatus = FollowStatus.PENDING;

        given()
            .when().post("/users/{id}/follow", target.id)
            .then()
            .statusCode(201)
            .body("status", is("PENDING"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void follow_alreadyFollowing_returns409_alreadyFollowing() {
        FollowServiceMock.forceConflictOnFollow = true;

        given()
            .when().post("/users/{id}/follow", UUID.randomUUID())
            .then()
            .statusCode(409)
            .body("error", is("already_following"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void follow_selfFollow_returns422_cannotFollowSelf() {
        FollowServiceMock.forceUnprocessableOnFollow = true;

        given()
            .when().post("/users/{id}/follow", UUID.randomUUID())
            .then()
            .statusCode(422)
            .body("error", is("cannot_follow_self"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void follow_unknownTarget_returns404() {
        FollowServiceMock.forceNotFoundOnTarget = true;

        given()
            .when().post("/users/{id}/follow", UUID.randomUUID())
            .then()
            .statusCode(404);
    }

    @Test
    void follow_unauthenticated_returns401() {
        given()
            .when().post("/users/{id}/follow", UUID.randomUUID())
            .then()
            .statusCode(401);
    }

    // =========================================================
    // DELETE /users/{id}/follow
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void unfollow_existingRow_returns204() {
        UUID targetId = UUID.randomUUID();
        followServiceMock.seedFollow(UUID.randomUUID(), targetId, FollowStatus.ACCEPTED);

        given()
            .when().delete("/users/{id}/follow", targetId)
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void unfollow_noRow_returns204_idempotent() {
        // Sentinel: DELETE on non-existing row must NOT raise 404 — returns 204.
        given()
            .when().delete("/users/{id}/follow", UUID.randomUUID())
            .then()
            .statusCode(204);
    }

    @Test
    void unfollow_unauthenticated_returns401() {
        given()
            .when().delete("/users/{id}/follow", UUID.randomUUID())
            .then()
            .statusCode(401);
    }

    // =========================================================
    // GET /users/{id}/followers | /following
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|bob")
    void getFollowers_publicProfile_returns200WithList() {
        // Note: the projected list is empty here because FollowResource.resolveUsers
        // calls the real User.list("id in ?1", ids) which doesn't see in-memory mock
        // seeds. Full content projection is covered by FollowServiceCoverageTest
        // against a real DevServices PostgreSQL instance.
        User alice = userServiceMock.seedUser("auth0|alice-flw", "alice-flw@example.com");
        alice.profilePublic = true;
        User charlie = userServiceMock.seedUser("auth0|charlie-flw", "charlie-flw@example.com");
        charlie.profilePublic = true;
        followServiceMock.seedFollow(charlie.id, alice.id, FollowStatus.ACCEPTED);

        given()
            .when().get("/users/{id}/followers", alice.id)
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|bob")
    void getFollowers_privateProfileNonOwner_returns404_antiOracle() {
        // Sentinel: ISSUE-93-aligned anti-oracle. Private profile + non-owner → 404.
        User alice = userServiceMock.seedUser("auth0|alice-priv-flw", "alice-priv-flw@example.com");
        alice.profilePublic = false;

        given()
            .when().get("/users/{id}/followers", alice.id)
            .then()
            .statusCode(404)
            .body("error", is("not_found"));
    }

    @Test
    @TestSecurity(user = "auth0|alice-priv-flw-owner")
    void getFollowers_privateProfileOwner_returns200() {
        User alice = userServiceMock.seedUser("auth0|alice-priv-flw-owner", "alice-priv-flw-owner@example.com");
        alice.profilePublic = false;

        given()
            .when().get("/users/{id}/followers", alice.id)
            .then()
            .statusCode(200);
    }

    @Test
    void getFollowers_anon_returns401() {
        given()
            .when().get("/users/{id}/followers", UUID.randomUUID())
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|bob")
    void getFollowing_publicProfile_returns200WithList() {
        // See note above on getFollowers_publicProfile_returns200WithList — full
        // content projection is covered by FollowServiceCoverageTest.
        User alice = userServiceMock.seedUser("auth0|alice-fwg", "alice-fwg@example.com");
        alice.profilePublic = true;
        User charlie = userServiceMock.seedUser("auth0|charlie-fwg", "charlie-fwg@example.com");
        charlie.profilePublic = true;
        followServiceMock.seedFollow(alice.id, charlie.id, FollowStatus.ACCEPTED);

        given()
            .when().get("/users/{id}/following", alice.id)
            .then()
            .statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|bob")
    void getFollowing_privateProfileNonOwner_returns404_antiOracle() {
        User alice = userServiceMock.seedUser("auth0|alice-priv-fwg", "alice-priv-fwg@example.com");
        alice.profilePublic = false;

        given()
            .when().get("/users/{id}/following", alice.id)
            .then()
            .statusCode(404)
            .body("error", is("not_found"));
    }

    // =========================================================
    // GET /users/me/follow-requests
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyFollowRequests_returns200WithPendingRows() {
        UUID alice = userServiceMock.seedUser("auth0|alice", "alice@example.com").id;
        followServiceMock.seedFollow(UUID.randomUUID(), alice, FollowStatus.PENDING);
        followServiceMock.seedFollow(UUID.randomUUID(), alice, FollowStatus.PENDING);

        given()
            .when().get("/users/me/follow-requests")
            .then()
            .statusCode(200)
            .body("$", hasSize(2))
            .body("[0].status", is("PENDING"))
            .body("[1].status", is("PENDING"));
    }

    @Test
    void getMyFollowRequests_unauthenticated_returns401() {
        given()
            .when().get("/users/me/follow-requests")
            .then()
            .statusCode(401);
    }

    // =========================================================
    // PATCH /follow-requests/{followId}/accept | /reject
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void acceptFollowRequest_pending_returns200_followDtoAccepted() {
        UUID alice = userServiceMock.seedUser("auth0|alice", "alice@example.com").id;
        Follow row = followServiceMock.seedFollow(UUID.randomUUID(), alice, FollowStatus.PENDING);

        given()
            .when().patch("/follow-requests/{followId}/accept", row.id)
            .then()
            .statusCode(200)
            .body("status", is("ACCEPTED"))
            .body("id", is(row.id.intValue()));
    }

    @Test
    @TestSecurity(user = "auth0|bob")
    void acceptFollowRequest_byNonTarget_returns403() {
        FollowServiceMock.forceForbiddenOnAccept = true;

        given()
            .when().patch("/follow-requests/{followId}/accept", 1L)
            .then()
            .statusCode(403)
            .body("error", is("forbidden"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void acceptFollowRequest_alreadyAccepted_returns409_invalidTransition() {
        UUID alice = userServiceMock.seedUser("auth0|alice", "alice@example.com").id;
        Follow row = followServiceMock.seedFollow(UUID.randomUUID(), alice, FollowStatus.ACCEPTED);
        FollowServiceMock.forceInvalidTransitionOnAccept = true;

        given()
            .when().patch("/follow-requests/{followId}/accept", row.id)
            .then()
            .statusCode(409)
            .body("error", is("invalid_transition"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void acceptFollowRequest_unknownId_returns404() {
        given()
            .when().patch("/follow-requests/{followId}/accept", 999L)
            .then()
            .statusCode(404);
    }

    @Test
    void acceptFollowRequest_unauthenticated_returns401() {
        given()
            .when().patch("/follow-requests/{followId}/accept", 1L)
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void rejectFollowRequest_pending_returns204() {
        UUID alice = userServiceMock.seedUser("auth0|alice", "alice@example.com").id;
        Follow row = followServiceMock.seedFollow(UUID.randomUUID(), alice, FollowStatus.PENDING);

        given()
            .when().patch("/follow-requests/{followId}/reject", row.id)
            .then()
            .statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|bob")
    void rejectFollowRequest_byNonTarget_returns403() {
        FollowServiceMock.forceForbiddenOnReject = true;

        given()
            .when().patch("/follow-requests/{followId}/reject", 1L)
            .then()
            .statusCode(403)
            .body("error", is("forbidden"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void rejectFollowRequest_alreadyAccepted_returns409_invalidTransition() {
        UUID alice = userServiceMock.seedUser("auth0|alice", "alice@example.com").id;
        Follow row = followServiceMock.seedFollow(UUID.randomUUID(), alice, FollowStatus.ACCEPTED);
        FollowServiceMock.forceInvalidTransitionOnReject = true;

        given()
            .when().patch("/follow-requests/{followId}/reject", row.id)
            .then()
            .statusCode(409)
            .body("error", is("invalid_transition"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void rejectFollowRequest_unknownId_returns404() {
        given()
            .when().patch("/follow-requests/{followId}/reject", 999L)
            .then()
            .statusCode(404);
    }

    // =========================================================
    // GET /users/{id} — verify SCRUM-138 enrichment is wired through
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|bob-counter")
    void getProfile_authNonOwner_includesNewCountersAndStatus() {
        UserServiceMock.mockFollowerCount = 5L;
        UserServiceMock.mockFollowingCount = 3L;
        UserServiceMock.mockFollowStatus = FollowStatus.PENDING;
        User alice = userServiceMock.seedUser("auth0|alice-counter", "alice-counter@example.com");
        alice.profilePublic = true;

        given()
            .when().get("/users/{id}", alice.id)
            .then()
            .statusCode(200)
            .body("followerCount", equalTo(5))
            .body("followingCount", equalTo(3))
            .body("followStatus", is("PENDING"));
    }

    @Test
    @TestSecurity(user = "auth0|alice-self")
    void getProfile_self_followStatusIsNull() {
        // Self never gets a non-null followStatus, even when fixtures inject one.
        UserServiceMock.mockFollowerCount = 7L;
        UserServiceMock.mockFollowingCount = 2L;
        UserServiceMock.mockFollowStatus = FollowStatus.ACCEPTED; // ignored on self
        User alice = userServiceMock.seedUser("auth0|alice-self", "alice-self@example.com");
        alice.profilePublic = false;

        given()
            .when().get("/users/{id}", alice.id)
            .then()
            .statusCode(200)
            .body("followerCount", equalTo(7))
            .body("followingCount", equalTo(2))
            .body("followStatus", nullValue());
    }

    @Test
    void getProfile_anon_publicProfile_includesZeroCountersAndNullStatus() {
        UserServiceMock.mockFollowerCount = 99L; // would be ignored — anon path zero-init
        User alice = userServiceMock.seedUser("auth0|alice-anon", "alice-anon@example.com");
        alice.profilePublic = true;

        given()
            .when().get("/users/{id}", alice.id)
            .then()
            .statusCode(200)
            .body("followerCount", equalTo(0))
            .body("followingCount", equalTo(0))
            .body("followStatus", nullValue());
    }
}
