package ch.unige.events.service;

import ch.unige.events.entity.Follow;
import ch.unige.events.entity.FollowStatus;
import ch.unige.events.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class FollowServiceCoverageTest {

    @Inject FollowService followService;
    @Inject EntityManager entityManager;

    // ── follow() ──────────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void follow_publicProfile_persistsAcceptedRow() {
        User alice = persistUser("auth0|alice-pub-1", "alice-pub-1@example.com", true);
        User bob = persistUser("auth0|bob-pub-1", "bob-pub-1@example.com", true);

        Follow row = followService.follow("auth0|alice-pub-1", bob.id);

        assertNotNull(row.id);
        assertEquals(alice.id, row.followerId);
        assertEquals(bob.id, row.followedId);
        assertEquals(FollowStatus.ACCEPTED, row.status);
        assertNotNull(row.createdAt);
    }

    @Test
    @TestTransaction
    void follow_privateProfile_persistsPendingRow() {
        persistUser("auth0|alice-priv-1", "alice-priv-1@example.com", true);
        User bob = persistUser("auth0|bob-priv-1", "bob-priv-1@example.com", false);

        Follow row = followService.follow("auth0|alice-priv-1", bob.id);

        assertEquals(FollowStatus.PENDING, row.status);
    }

    @Test
    @TestTransaction
    void follow_alreadyFollowing_throwsConflict() {
        persistUser("auth0|alice-dup", "alice-dup@example.com", true);
        User bob = persistUser("auth0|bob-dup", "bob-dup@example.com", true);
        followService.follow("auth0|alice-dup", bob.id);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> followService.follow("auth0|alice-dup", bob.id));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void follow_selfFollow_throwsUnprocessable() {
        User alice = persistUser("auth0|alice-self-fl", "alice-self-fl@example.com", true);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> followService.follow("auth0|alice-self-fl", alice.id));
        assertEquals(422, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void follow_targetNotFound_throwsNotFound() {
        persistUser("auth0|alice-tgt-nf", "alice-tgt-nf@example.com", true);

        assertThrows(NotFoundException.class,
                () -> followService.follow("auth0|alice-tgt-nf", UUID.randomUUID()));
    }

    @Test
    @TestTransaction
    void follow_callerNotProvisioned_throwsNotFound() {
        User bob = persistUser("auth0|bob-cnp", "bob-cnp@example.com", true);

        assertThrows(NotFoundException.class,
                () -> followService.follow("auth0|ghost-not-in-db", bob.id));
    }

    // ── unfollow() ────────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void unfollow_existingRow_deletesIt() {
        User alice = persistUser("auth0|alice-uf", "alice-uf@example.com", true);
        User bob = persistUser("auth0|bob-uf", "bob-uf@example.com", true);
        followService.follow("auth0|alice-uf", bob.id);

        followService.unfollow("auth0|alice-uf", bob.id);

        assertTrue(Follow.findByFollowerAndFollowed(alice.id, bob.id).isEmpty());
    }

    @Test
    @TestTransaction
    void unfollow_noRow_isIdempotent() {
        persistUser("auth0|alice-uf-no", "alice-uf-no@example.com", true);
        User bob = persistUser("auth0|bob-uf-no", "bob-uf-no@example.com", true);

        // No follow row exists — must NOT throw.
        followService.unfollow("auth0|alice-uf-no", bob.id);
    }

    // ── acceptRequest() ───────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void acceptRequest_byTarget_setsAccepted() {
        User alice = persistUser("auth0|alice-acc", "alice-acc@example.com", true);
        User bob = persistUser("auth0|bob-acc", "bob-acc@example.com", false);
        Follow pending = persistFollow(alice.id, bob.id, FollowStatus.PENDING);

        Follow accepted = followService.acceptRequest("auth0|bob-acc", pending.id);

        assertEquals(FollowStatus.ACCEPTED, accepted.status);
    }

    @Test
    @TestTransaction
    void acceptRequest_byNonTarget_throwsForbidden() {
        User alice = persistUser("auth0|alice-acc-nt", "alice-acc-nt@example.com", true);
        User bob = persistUser("auth0|bob-acc-nt", "bob-acc-nt@example.com", false);
        persistUser("auth0|carol-acc-nt", "carol-acc-nt@example.com", true);
        Follow pending = persistFollow(alice.id, bob.id, FollowStatus.PENDING);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> followService.acceptRequest("auth0|carol-acc-nt", pending.id));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void acceptRequest_alreadyAccepted_throwsInvalidTransition() {
        User alice = persistUser("auth0|alice-acc-alr", "alice-acc-alr@example.com", true);
        User bob = persistUser("auth0|bob-acc-alr", "bob-acc-alr@example.com", false);
        Follow accepted = persistFollow(alice.id, bob.id, FollowStatus.ACCEPTED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> followService.acceptRequest("auth0|bob-acc-alr", accepted.id));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void acceptRequest_unknownId_throwsNotFound() {
        persistUser("auth0|bob-acc-unk", "bob-acc-unk@example.com", false);

        assertThrows(NotFoundException.class,
                () -> followService.acceptRequest("auth0|bob-acc-unk", 999_999L));
    }

    // ── rejectRequest() ───────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void rejectRequest_pending_deletesRow() {
        User alice = persistUser("auth0|alice-rej", "alice-rej@example.com", true);
        User bob = persistUser("auth0|bob-rej", "bob-rej@example.com", false);
        Follow pending = persistFollow(alice.id, bob.id, FollowStatus.PENDING);

        followService.rejectRequest("auth0|bob-rej", pending.id);

        assertTrue(Follow.<Follow>findByIdOptional(pending.id).isEmpty());
    }

    @Test
    @TestTransaction
    void rejectRequest_byNonTarget_throwsForbidden() {
        User alice = persistUser("auth0|alice-rej-nt", "alice-rej-nt@example.com", true);
        User bob = persistUser("auth0|bob-rej-nt", "bob-rej-nt@example.com", false);
        persistUser("auth0|carol-rej-nt", "carol-rej-nt@example.com", true);
        Follow pending = persistFollow(alice.id, bob.id, FollowStatus.PENDING);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> followService.rejectRequest("auth0|carol-rej-nt", pending.id));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void rejectRequest_alreadyAccepted_throwsInvalidTransition() {
        User alice = persistUser("auth0|alice-rej-acc", "alice-rej-acc@example.com", true);
        User bob = persistUser("auth0|bob-rej-acc", "bob-rej-acc@example.com", false);
        Follow accepted = persistFollow(alice.id, bob.id, FollowStatus.ACCEPTED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> followService.rejectRequest("auth0|bob-rej-acc", accepted.id));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void rejectRequest_unknownId_throwsNotFound() {
        persistUser("auth0|bob-rej-unk", "bob-rej-unk@example.com", false);

        assertThrows(NotFoundException.class,
                () -> followService.rejectRequest("auth0|bob-rej-unk", 999_999L));
    }

    @Test
    @TestTransaction
    void rejectRequest_followerCanReFollowAfterReject() {
        // Sentinel: after a reject (= DELETE row), re-following must succeed (no 409).
        User alice = persistUser("auth0|alice-refl", "alice-refl@example.com", true);
        User bob = persistUser("auth0|bob-refl", "bob-refl@example.com", false);
        Follow pending = persistFollow(alice.id, bob.id, FollowStatus.PENDING);
        followService.rejectRequest("auth0|bob-refl", pending.id);

        Follow newRow = followService.follow("auth0|alice-refl", bob.id);
        assertNotNull(newRow.id);
        assertEquals(FollowStatus.PENDING, newRow.status);
    }

    // ── countFollowers / countFollowing ───────────────────────────────────────

    @Test
    @TestTransaction
    void countFollowers_returnsAcceptedOnly() {
        User alice = persistUser("auth0|alice-cf", "alice-cf@example.com", true);
        User bob = persistUser("auth0|bob-cf", "bob-cf@example.com", true);
        User carol = persistUser("auth0|carol-cf", "carol-cf@example.com", true);
        User dave = persistUser("auth0|dave-cf", "dave-cf@example.com", true);
        persistFollow(bob.id, alice.id, FollowStatus.ACCEPTED);
        persistFollow(carol.id, alice.id, FollowStatus.ACCEPTED);
        persistFollow(dave.id, alice.id, FollowStatus.PENDING);

        assertEquals(2L, followService.countFollowers(alice.id));
    }

    @Test
    @TestTransaction
    void countFollowing_returnsAcceptedOnly() {
        User alice = persistUser("auth0|alice-cfwg", "alice-cfwg@example.com", true);
        User bob = persistUser("auth0|bob-cfwg", "bob-cfwg@example.com", true);
        User carol = persistUser("auth0|carol-cfwg", "carol-cfwg@example.com", true);
        User dave = persistUser("auth0|dave-cfwg", "dave-cfwg@example.com", true);
        persistFollow(alice.id, bob.id, FollowStatus.ACCEPTED);
        persistFollow(alice.id, carol.id, FollowStatus.ACCEPTED);
        persistFollow(alice.id, dave.id, FollowStatus.PENDING);

        assertEquals(2L, followService.countFollowing(alice.id));
    }

    // ── getStatusBetween ──────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void getStatusBetween_self_returnsNull() {
        User alice = persistUser("auth0|alice-sb-self", "alice-sb-self@example.com", true);

        assertNull(followService.getStatusBetween(alice.id, alice.id));
    }

    @Test
    @TestTransaction
    void getStatusBetween_pendingRow_returnsPending() {
        User alice = persistUser("auth0|alice-sb-pend", "alice-sb-pend@example.com", true);
        User bob = persistUser("auth0|bob-sb-pend", "bob-sb-pend@example.com", false);
        persistFollow(alice.id, bob.id, FollowStatus.PENDING);

        assertEquals(FollowStatus.PENDING, followService.getStatusBetween(alice.id, bob.id));
    }

    @Test
    @TestTransaction
    void getStatusBetween_acceptedRow_returnsAccepted() {
        User alice = persistUser("auth0|alice-sb-acc", "alice-sb-acc@example.com", true);
        User bob = persistUser("auth0|bob-sb-acc", "bob-sb-acc@example.com", true);
        persistFollow(alice.id, bob.id, FollowStatus.ACCEPTED);

        assertEquals(FollowStatus.ACCEPTED, followService.getStatusBetween(alice.id, bob.id));
    }

    @Test
    @TestTransaction
    void getStatusBetween_noRow_returnsNull() {
        User alice = persistUser("auth0|alice-sb-no", "alice-sb-no@example.com", true);
        User bob = persistUser("auth0|bob-sb-no", "bob-sb-no@example.com", true);

        assertNull(followService.getStatusBetween(alice.id, bob.id));
    }

    @Test
    @TestTransaction
    void getStatusBetween_nullCaller_returnsNull() {
        User bob = persistUser("auth0|bob-sb-null", "bob-sb-null@example.com", true);

        assertNull(followService.getStatusBetween(null, bob.id));
    }

    // ── findAcceptedFollowedIds — sentinel SCRUM-168 ──────────────────────────

    @Test
    @TestTransaction
    void findAcceptedFollowedIds_returnsOnlyAcceptedUuids() {
        // SCRUM-168 sentinel : only ACCEPTED outgoing follows of alice are returned.
        User alice = persistUser("auth0|alice-fafi", "alice-fafi@example.com", true);
        User bob = persistUser("auth0|bob-fafi", "bob-fafi@example.com", true);
        User carol = persistUser("auth0|carol-fafi", "carol-fafi@example.com", false);
        User dave = persistUser("auth0|dave-fafi", "dave-fafi@example.com", true);
        persistFollow(alice.id, bob.id, FollowStatus.ACCEPTED);     // included
        persistFollow(alice.id, carol.id, FollowStatus.PENDING);    // PENDING — excluded
        persistFollow(alice.id, dave.id, FollowStatus.ACCEPTED);    // included
        persistFollow(bob.id, alice.id, FollowStatus.ACCEPTED);     // inverse — excluded

        List<UUID> ids = Follow.findAcceptedFollowedIds(alice.id);

        assertThat(ids, containsInAnyOrder(bob.id, dave.id));
        assertEquals(2, ids.size());
    }

    @Test
    @TestTransaction
    void findAcceptedFollowedIds_emptyForUserWithNoFollows() {
        User alice = persistUser("auth0|alice-fafi-empty", "alice-fafi-empty@example.com", true);

        List<UUID> ids = Follow.findAcceptedFollowedIds(alice.id);

        assertNotNull(ids);
        assertTrue(ids.isEmpty());
    }

    // ── Listings & pagination ─────────────────────────────────────────────────

    @Test
    @TestTransaction
    void getFollowers_excludesPendingRows() {
        User alice = persistUser("auth0|alice-gf-excl", "alice-gf-excl@example.com", true);
        User bob = persistUser("auth0|bob-gf-excl", "bob-gf-excl@example.com", true);
        User carol = persistUser("auth0|carol-gf-excl", "carol-gf-excl@example.com", true);
        persistFollow(bob.id, alice.id, FollowStatus.ACCEPTED);
        persistFollow(carol.id, alice.id, FollowStatus.PENDING);

        List<Follow> followers = followService.getFollowers(alice.id, 0, 20);

        assertEquals(1, followers.size());
        assertEquals(bob.id, followers.get(0).followerId);
    }

    @Test
    @TestTransaction
    void getFollowers_paginated_respectsPageAndSize() {
        User alice = persistUser("auth0|alice-pg", "alice-pg@example.com", true);
        for (int i = 0; i < 5; i++) {
            User u = persistUser("auth0|fwr-" + i, "fwr-" + i + "@example.com", true);
            persistFollow(u.id, alice.id, FollowStatus.ACCEPTED);
        }

        List<Follow> page0 = followService.getFollowers(alice.id, 0, 2);
        List<Follow> page1 = followService.getFollowers(alice.id, 1, 2);
        List<Follow> page2 = followService.getFollowers(alice.id, 2, 2);

        assertEquals(2, page0.size());
        assertEquals(2, page1.size());
        assertEquals(1, page2.size());
    }

    @Test
    @TestTransaction
    void getFollowing_returnsOnlyAcceptedOutgoing() {
        User alice = persistUser("auth0|alice-gfwg", "alice-gfwg@example.com", true);
        User bob = persistUser("auth0|bob-gfwg", "bob-gfwg@example.com", true);
        User carol = persistUser("auth0|carol-gfwg", "carol-gfwg@example.com", false);
        persistFollow(alice.id, bob.id, FollowStatus.ACCEPTED);
        persistFollow(alice.id, carol.id, FollowStatus.PENDING);

        List<Follow> following = followService.getFollowing(alice.id, 0, 20);

        assertEquals(1, following.size());
        assertEquals(bob.id, following.get(0).followedId);
    }

    @Test
    @TestTransaction
    void getPendingRequests_returnsOnlyPendingForTarget() {
        User alice = persistUser("auth0|alice-gpr", "alice-gpr@example.com", false);
        User bob = persistUser("auth0|bob-gpr", "bob-gpr@example.com", true);
        User carol = persistUser("auth0|carol-gpr", "carol-gpr@example.com", true);
        persistFollow(bob.id, alice.id, FollowStatus.PENDING);
        persistFollow(carol.id, alice.id, FollowStatus.ACCEPTED);

        List<Follow> pending = followService.getPendingRequests("auth0|alice-gpr", 0, 20);

        assertEquals(1, pending.size());
        assertEquals(FollowStatus.PENDING, pending.get(0).status);
        assertEquals(bob.id, pending.get(0).followerId);
    }

    @Test
    @TestTransaction
    void getPendingRequests_callerNotProvisioned_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> followService.getPendingRequests("auth0|ghost-pr", 0, 20));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User persistUser(String auth0Id, String email, boolean profilePublic) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        user.profilePublic = profilePublic;
        user.createdAt = LocalDateTime.now();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Follow persistFollow(UUID followerId, UUID followedId, FollowStatus status) {
        Follow row = new Follow();
        row.followerId = followerId;
        row.followedId = followedId;
        row.status = status;
        row.createdAt = LocalDateTime.now();
        entityManager.persist(row);
        entityManager.flush();
        return row;
    }
}
