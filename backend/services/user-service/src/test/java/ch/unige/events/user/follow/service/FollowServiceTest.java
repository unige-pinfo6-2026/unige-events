package ch.unige.events.user.follow.service;

import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.follow.entity.Follow;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
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
    @Inject RecordingFollowLifecycleObserver recorder;

    @BeforeEach
    void resetRecorder() {
        recorder.reset();
    }

    private List<FollowLifecycleEvent.Type> firedTypes() {
        return recorder.events().stream().map(FollowLifecycleEvent::type).toList();
    }

    private FollowLifecycleEvent firedOfType(FollowLifecycleEvent.Type type) {
        return recorder.events().stream()
                .filter(e -> e.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " event fired"));
    }

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
    void follow_publicProfile_firesSingleFollowedEvent() {
        persistUser("auth0|fs-fire-pub-a", "fs-fire-pub-a@example.com", true);
        User bob = persistUser("auth0|fs-fire-pub-b", "fs-fire-pub-b@example.com", true);

        followService.follow("auth0|fs-fire-pub-a", bob.id);

        // Public follow → exactly one FOLLOWED, never REQUESTED/ACCEPTED.
        assertEquals(List.of(FollowLifecycleEvent.Type.FOLLOWED), firedTypes());
    }

    @Test
    @TestTransaction
    void follow_privateProfile_firesSingleRequestedEvent() {
        persistUser("auth0|fs-fire-priv-a", "fs-fire-priv-a@example.com", true);
        User bob = persistUser("auth0|fs-fire-priv-b", "fs-fire-priv-b@example.com", false);

        followService.follow("auth0|fs-fire-priv-a", bob.id);

        assertEquals(List.of(FollowLifecycleEvent.Type.REQUESTED), firedTypes());
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
        User alice = persistUser("auth0|fs-uf-no-a", "fs-uf-no-a@example.com", true);
        User bob = persistUser("auth0|fs-uf-no-b", "fs-uf-no-b@example.com", true);

        followService.unfollow("auth0|fs-uf-no-a", bob.id);

        // Idempotent no-op: there was no row, and none was created.
        assertTrue(Follow.findByFollowerAndFollowed(alice.id, bob.id).isEmpty());
    }

    // ── removeFollower ────────────────────────────────────────────────────

    @Test
    @TestTransaction
    void removeFollower_existingRow_deletesIt() {
        // Alice follows Bob ; Bob removes Alice from his followers.
        User alice = persistUser("auth0|fs-rf-a", "fs-rf-a@example.com", true);
        User bob = persistUser("auth0|fs-rf-b", "fs-rf-b@example.com", true);
        persistFollow(alice.id, bob.id, FollowStatus.ACCEPTED);

        followService.removeFollower("auth0|fs-rf-b", alice.id);

        assertTrue(Follow.findByFollowerAndFollowed(alice.id, bob.id).isEmpty());
    }

    @Test
    @TestTransaction
    void removeFollower_noRow_isIdempotent() {
        persistUser("auth0|fs-rf-no-a", "fs-rf-no-a@example.com", true);
        User bob = persistUser("auth0|fs-rf-no-b", "fs-rf-no-b@example.com", true);

        // Bob removes someone who isn't following him — no-op, no exception.
        followService.removeFollower("auth0|fs-rf-no-b", UUID.randomUUID());
        assertTrue(Follow.findFollowersOf(bob.id, 0, 20).isEmpty());
    }

    @Test
    @TestTransaction
    void removeFollower_onlyDropsTheInboundRow_notTheReverse() {
        // Mutual follow: Alice→Bob and Bob→Alice. Bob removing Alice as a
        // follower must delete only Alice→Bob, leaving Bob→Alice intact.
        User alice = persistUser("auth0|fs-rf-mut-a", "fs-rf-mut-a@example.com", true);
        User bob = persistUser("auth0|fs-rf-mut-b", "fs-rf-mut-b@example.com", true);
        persistFollow(alice.id, bob.id, FollowStatus.ACCEPTED);
        persistFollow(bob.id, alice.id, FollowStatus.ACCEPTED);

        followService.removeFollower("auth0|fs-rf-mut-b", alice.id);

        assertTrue(Follow.findByFollowerAndFollowed(alice.id, bob.id).isEmpty(), "inbound row dropped");
        assertTrue(Follow.findByFollowerAndFollowed(bob.id, alice.id).isPresent(), "reverse row kept");
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
    void acceptRequest_firesBothFollowedAndAcceptedToTheRightParties() {
        // A = requester (alice), B = target/private (bob).
        User alice = persistUser("auth0|fs-acc-fire-a", "fs-acc-fire-a@example.com", true);
        User bob = persistUser("auth0|fs-acc-fire-b", "fs-acc-fire-b@example.com", false);
        Follow pending = persistFollow(alice.id, bob.id, FollowStatus.PENDING);
        recorder.reset();

        followService.acceptRequest("auth0|fs-acc-fire-b", pending.id);

        // Two notifications on accept: FOLLOW_ACCEPTED → A, NEW_FOLLOWER → B.
        assertTrue(firedTypes().contains(FollowLifecycleEvent.Type.ACCEPTED), "ACCEPTED must fire");
        assertTrue(firedTypes().contains(FollowLifecycleEvent.Type.FOLLOWED), "FOLLOWED must fire");

        FollowLifecycleEvent accepted = firedOfType(FollowLifecycleEvent.Type.ACCEPTED);
        assertEquals(alice.id, accepted.followerId());
        assertEquals(bob.id, accepted.followedId());

        // FOLLOWED carries follower=A, followed=B → UserFollowedConsumer targets B
        // ("A a commencé à vous suivre").
        FollowLifecycleEvent followed = firedOfType(FollowLifecycleEvent.Type.FOLLOWED);
        assertEquals(alice.id, followed.followerId());
        assertEquals(bob.id, followed.followedId());
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
    void rejectRequest_firesNoLifecycleEvent() {
        User alice = persistUser("auth0|fs-rej-fire-a", "fs-rej-fire-a@example.com", true);
        User bob = persistUser("auth0|fs-rej-fire-b", "fs-rej-fire-b@example.com", false);
        Follow pending = persistFollow(alice.id, bob.id, FollowStatus.PENDING);
        recorder.reset();

        followService.rejectRequest("auth0|fs-rej-fire-b", pending.id);

        // Reject just deletes the row — A is never notified.
        assertTrue(recorder.events().isEmpty(), "reject must not fire any lifecycle event");
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
        user.createdAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Follow persistFollow(UUID followerId, UUID followedId, FollowStatus status) {
        Follow f = new Follow();
        f.followerId = followerId;
        f.followedId = followedId;
        f.status = status;
        f.createdAt = LocalDateTime.of(2025, 1, 1, 12, 0);
        entityManager.persist(f);
        entityManager.flush();
        return f;
    }
}
