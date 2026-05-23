package ch.unige.events.user.follow.service;

import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.follow.entity.Follow;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage suite for {@link FollowService}. Drives the service against
 * the real Postgres dev-services container — exercises the JPA write
 * paths (persist + flush + race-on-create translation) and the read
 * filters (status=ACCEPTED on count/list).
 */
@QuarkusTest
class FollowServiceTest {

    @Inject FollowService followService;
    @Inject EntityManager entityManager;

    // ── follow ────────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void follow_publicProfile_persistsAcceptedRow() {
        User alice = persistUser("auth0|fs-pub-a", "fs-pub-a@example.com", true);
        User bob = persistUser("auth0|fs-pub-b", "fs-pub-b@example.com", true);

        Follow row = followService.follow("auth0|fs-pub-a", bob.id);

        assertNotNull(row.id);
        assertEquals(alice.id, row.followerId);
        assertEquals(bob.id, row.followedId);
        assertEquals(FollowStatus.ACCEPTED, row.status);
        assertNotNull(row.createdAt);
    }

    @Test
    @TestTransaction
    void follow_privateProfile_persistsPendingRow() {
        persistUser("auth0|fs-priv-a", "fs-priv-a@example.com", true);
        User bob = persistUser("auth0|fs-priv-b", "fs-priv-b@example.com", false);

        Follow row = followService.follow("auth0|fs-priv-a", bob.id);

        assertEquals(FollowStatus.PENDING, row.status);
    }

    @Test
    @TestTransaction
    void follow_alreadyFollowing_throwsConflict() {
        persistUser("auth0|fs-dup-a", "fs-dup-a@example.com", true);
        User bob = persistUser("auth0|fs-dup-b", "fs-dup-b@example.com", true);
        followService.follow("auth0|fs-dup-a", bob.id);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> followService.follow("auth0|fs-dup-a", bob.id));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void follow_targetNotFound_throwsNotFound() {
        persistUser("auth0|fs-tgt-nf", "fs-tgt-nf@example.com", true);

        assertThrows(NotFoundException.class,
                () -> followService.follow("auth0|fs-tgt-nf", UUID.randomUUID()));
    }

    @Test
    @TestTransaction
    void follow_callerNotProvisioned_throwsNotFound() {
        User bob = persistUser("auth0|fs-cnp-b", "fs-cnp-b@example.com", true);

        assertThrows(NotFoundException.class,
                () -> followService.follow("auth0|fs-ghost", bob.id));
    }

    // ── unfollow ──────────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void unfollow_existingRow_deletesIt() {
        User alice = persistUser("auth0|fs-uf-a", "fs-uf-a@example.com", true);
        User bob = persistUser("auth0|fs-uf-b", "fs-uf-b@example.com", true);
        followService.follow("auth0|fs-uf-a", bob.id);

        followService.unfollow("auth0|fs-uf-a", bob.id);

        assertTrue(Follow.findByFollowerAndFollowed(alice.id, bob.id).isEmpty());
    }

    @Test
    @TestTransaction
    void unfollow_noRow_isIdempotent() {
        persistUser("auth0|fs-uf-no-a", "fs-uf-no-a@example.com", true);
        User bob = persistUser("auth0|fs-uf-no-b", "fs-uf-no-b@example.com", true);

        followService.unfollow("auth0|fs-uf-no-a", bob.id);
        // No exception expected.
    }

    // ── acceptRequest ─────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void acceptRequest_byTarget_setsAccepted() {
        User alice = persistUser("auth0|fs-acc-a", "fs-acc-a@example.com", true);
        User bob = persistUser("auth0|fs-acc-b", "fs-acc-b@example.com", false);
        Follow pending = persistFollow(alice.id, bob.id, FollowStatus.PENDING);

        Follow accepted = followService.acceptRequest("auth0|fs-acc-b", pending.id);
        assertEquals(FollowStatus.ACCEPTED, accepted.status);
    }

    @Test
    @TestTransaction
    void acceptRequest_byNonTarget_throwsForbidden() {
        User alice = persistUser("auth0|fs-acc-nt-a", "fs-acc-nt-a@example.com", true);
        User bob = persistUser("auth0|fs-acc-nt-b", "fs-acc-nt-b@example.com", false);
        persistUser("auth0|fs-acc-nt-c", "fs-acc-nt-c@example.com", true);
        Follow pending = persistFollow(alice.id, bob.id, FollowStatus.PENDING);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> followService.acceptRequest("auth0|fs-acc-nt-c", pending.id));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void acceptRequest_alreadyAccepted_throwsInvalidTransition() {
        User alice = persistUser("auth0|fs-acc-alr-a", "fs-acc-alr-a@example.com", true);
        User bob = persistUser("auth0|fs-acc-alr-b", "fs-acc-alr-b@example.com", false);
        Follow accepted = persistFollow(alice.id, bob.id, FollowStatus.ACCEPTED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> followService.acceptRequest("auth0|fs-acc-alr-b", accepted.id));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void acceptRequest_unknownId_throwsNotFound() {
        persistUser("auth0|fs-acc-unk", "fs-acc-unk@example.com", false);

        assertThrows(NotFoundException.class,
                () -> followService.acceptRequest("auth0|fs-acc-unk", 999_999L));
    }

    // ── rejectRequest ─────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void rejectRequest_pending_deletesRow() {
        User alice = persistUser("auth0|fs-rej-a", "fs-rej-a@example.com", true);
        User bob = persistUser("auth0|fs-rej-b", "fs-rej-b@example.com", false);
        Follow pending = persistFollow(alice.id, bob.id, FollowStatus.PENDING);

        followService.rejectRequest("auth0|fs-rej-b", pending.id);

        assertTrue(Follow.<Follow>findByIdOptional(pending.id).isEmpty());
    }

    @Test
    @TestTransaction
    void rejectRequest_byNonTarget_throwsForbidden() {
        User alice = persistUser("auth0|fs-rej-nt-a", "fs-rej-nt-a@example.com", true);
        User bob = persistUser("auth0|fs-rej-nt-b", "fs-rej-nt-b@example.com", false);
        persistUser("auth0|fs-rej-nt-c", "fs-rej-nt-c@example.com", true);
        Follow pending = persistFollow(alice.id, bob.id, FollowStatus.PENDING);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> followService.rejectRequest("auth0|fs-rej-nt-c", pending.id));
        assertEquals(403, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void rejectRequest_alreadyAccepted_throwsInvalidTransition() {
        User alice = persistUser("auth0|fs-rej-acc-a", "fs-rej-acc-a@example.com", true);
        User bob = persistUser("auth0|fs-rej-acc-b", "fs-rej-acc-b@example.com", false);
        Follow accepted = persistFollow(alice.id, bob.id, FollowStatus.ACCEPTED);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> followService.rejectRequest("auth0|fs-rej-acc-b", accepted.id));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void rejectRequest_unknownId_throwsNotFound() {
        persistUser("auth0|fs-rej-unk", "fs-rej-unk@example.com", false);

        assertThrows(NotFoundException.class,
                () -> followService.rejectRequest("auth0|fs-rej-unk", 999_999L));
    }

    // ── getFollowers / getFollowing / getPendingRequests ──────────────────

    @Test
    @TestTransaction
    void getFollowers_returnsAcceptedOnly() {
        User alice = persistUser("auth0|fs-gf-a", "fs-gf-a@example.com", true);
        User bob = persistUser("auth0|fs-gf-b", "fs-gf-b@example.com", true);
        User carol = persistUser("auth0|fs-gf-c", "fs-gf-c@example.com", true);
        User dave = persistUser("auth0|fs-gf-d", "fs-gf-d@example.com", true);
        persistFollow(bob.id, alice.id, FollowStatus.ACCEPTED);
        persistFollow(carol.id, alice.id, FollowStatus.ACCEPTED);
        persistFollow(dave.id, alice.id, FollowStatus.PENDING);

        List<Follow> followers = followService.getFollowers(alice.id, 0, 20);
        assertEquals(2, followers.size());
        followers.forEach(f -> assertEquals(FollowStatus.ACCEPTED, f.status));
    }

    @Test
    @TestTransaction
    void getFollowing_returnsAcceptedOnly() {
        User alice = persistUser("auth0|fs-gw-a", "fs-gw-a@example.com", true);
        User bob = persistUser("auth0|fs-gw-b", "fs-gw-b@example.com", true);
        User carol = persistUser("auth0|fs-gw-c", "fs-gw-c@example.com", false);
        persistFollow(alice.id, bob.id, FollowStatus.ACCEPTED);
        persistFollow(alice.id, carol.id, FollowStatus.PENDING);

        List<Follow> following = followService.getFollowing(alice.id, 0, 20);
        assertEquals(1, following.size());
        assertEquals(bob.id, following.get(0).followedId);
    }

    @Test
    @TestTransaction
    void getPendingRequests_returnsPendingForCallerOnly() {
        User alice = persistUser("auth0|fs-gpr-a", "fs-gpr-a@example.com", true);
        User bob = persistUser("auth0|fs-gpr-b", "fs-gpr-b@example.com", false);
        User carol = persistUser("auth0|fs-gpr-c", "fs-gpr-c@example.com", true);
        persistFollow(alice.id, bob.id, FollowStatus.PENDING);
        persistFollow(carol.id, bob.id, FollowStatus.PENDING);
        persistFollow(alice.id, carol.id, FollowStatus.ACCEPTED);

        List<Follow> pending = followService.getPendingRequests("auth0|fs-gpr-b", 0, 20);
        assertEquals(2, pending.size());
        pending.forEach(f -> assertEquals(bob.id, f.followedId));
    }

    @Test
    @TestTransaction
    void getPendingRequests_callerNotProvisioned_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> followService.getPendingRequests("auth0|fs-gpr-ghost", 0, 20));
    }

    // ── assertProfileVisible ──────────────────────────────────────────────

    @Test
    @TestTransaction
    void assertProfileVisible_publicProfile_passes() {
        User user = persistUser("auth0|fs-apv-pub", "fs-apv-pub@example.com", true);
        // Should not throw.
        followService.assertProfileVisible(user.id, "auth0|some-caller");
    }

    @Test
    @TestTransaction
    void assertProfileVisible_privateOwner_passes() {
        User user = persistUser("auth0|fs-apv-priv-self", "fs-apv-priv-self@example.com", false);
        followService.assertProfileVisible(user.id, "auth0|fs-apv-priv-self");
    }

    @Test
    @TestTransaction
    void assertProfileVisible_privateNonOwner_throwsNotFound() {
        User user = persistUser("auth0|fs-apv-priv-other-tgt", "fs-apv-priv-other-tgt@example.com", false);
        assertThrows(NotFoundException.class,
                () -> followService.assertProfileVisible(user.id, "auth0|fs-apv-priv-other-caller"));
    }

    @Test
    @TestTransaction
    void assertProfileVisible_unknownTarget_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> followService.assertProfileVisible(UUID.randomUUID(), "auth0|caller"));
    }

    // ── Follow static helpers ─────────────────────────────────────────────

    @Test
    @TestTransaction
    void follow_count_helpers() {
        User alice = persistUser("auth0|fs-cnt-a", "fs-cnt-a@example.com", true);
        User bob = persistUser("auth0|fs-cnt-b", "fs-cnt-b@example.com", true);
        User carol = persistUser("auth0|fs-cnt-c", "fs-cnt-c@example.com", true);
        persistFollow(bob.id, alice.id, FollowStatus.ACCEPTED);
        persistFollow(carol.id, alice.id, FollowStatus.PENDING);
        persistFollow(alice.id, bob.id, FollowStatus.ACCEPTED);

        assertEquals(1L, Follow.countFollowersOf(alice.id), "PENDING excluded from followerCount");
        assertEquals(1L, Follow.countFollowingOf(alice.id));
    }

    // ── assertProfileVisible (ISSUE-93 anti-oracle) ────────────────────────

    @Test
    @TestTransaction
    void assertProfileVisible_privateProfile_anonymousCaller_throwsNotFound() {
        User priv = persistUser("auth0|fs-apv-anon", "fs-apv-anon@example.com", false);
        // callerAuth0Id == null → isOwner false → private profile closes the oracle with 404.
        assertThrows(NotFoundException.class,
                () -> followService.assertProfileVisible(priv.id, null));
    }

    @Test
    @TestTransaction
    void assertProfileVisible_publicProfile_anonymousCaller_passes() {
        User pub = persistUser("auth0|fs-apv-pub", "fs-apv-pub@example.com", true);
        followService.assertProfileVisible(pub.id, null); // must not throw
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private User persistUser(String auth0Id, String email, boolean profilePublic) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        // SCRUM-169 — derive a unique username so NOT NULL + UNIQUE pass.
        user.username = ch.unige.events.user.service.UsernameGenerator.resolveAvailable(
                ch.unige.events.user.service.UsernameGenerator.slugify(null,
                        email.contains("@") ? email.substring(0, email.indexOf('@')) : email,
                        null),
                candidate -> ch.unige.events.user.entity.User.findByUsername(candidate).isPresent()
        );
        user.profilePublic = profilePublic;
        user.createdAt = LocalDateTime.now();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Follow persistFollow(UUID followerId, UUID followedId, FollowStatus status) {
        Follow f = new Follow();
        f.followerId = followerId;
        f.followedId = followedId;
        f.status = status;
        f.createdAt = LocalDateTime.now();
        entityManager.persist(f);
        entityManager.flush();
        return f;
    }
}
