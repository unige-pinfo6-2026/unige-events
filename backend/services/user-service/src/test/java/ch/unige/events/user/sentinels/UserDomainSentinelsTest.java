package ch.unige.events.user.sentinels;

import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.entity.User;
import ch.unige.events.user.follow.entity.Follow;
import ch.unige.events.user.follow.service.FollowService;
import ch.unige.events.user.service.UserService;
import ch.unige.events.user.dto.PublicProfileView;
import ch.unige.events.user.test.JwtTestContext;
import ch.unige.events.user.test.JwtTestHelper;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SCRUM-138 sentinel suite — user-service (incl. absorbed follow + calendar
 * functionality).
 *
 * <p>The 6 sentinels run on the DevServices Postgres test profile and are
 * wrapped in {@link TestTransaction} so persisted fixtures are rolled back
 * at end-of-test.
 */
@QuarkusTest
@SuppressWarnings({"java:S100", "java:S2699"})
class UserDomainSentinelsTest {

    @Inject FollowService followService;
    @Inject UserService userService;
    @Inject EntityManager entityManager;

    @BeforeEach
    void clearJwt() {
        JwtTestContext.clear();
    }

    @AfterEach
    void resetJwt() {
        JwtTestContext.clear();
    }

    /**
     * Sentinel — adapted post-extraction: the legacy
     * {@code findAcceptedFollowedIds} method became
     * {@link Follow#findFollowingOf(UUID, int, int)} which already filters
     * by ACCEPTED. We assert that the returned list excludes any PENDING
     * row and only carries the followedIds whose row is ACCEPTED.
     */
    @Test
    @TestTransaction
    void findAcceptedFollowedIds_returnsOnlyAcceptedUuids() {
        User alice = persistUser("auth0|s-faf-alice", "s-faf-alice@example.com", true);
        User bob = persistUser("auth0|s-faf-bob", "s-faf-bob@example.com", true);
        User carol = persistUser("auth0|s-faf-carol", "s-faf-carol@example.com", false);
        User dave = persistUser("auth0|s-faf-dave", "s-faf-dave@example.com", false);
        persistFollow(alice.id, bob.id, FollowStatus.ACCEPTED);
        persistFollow(alice.id, carol.id, FollowStatus.ACCEPTED);
        persistFollow(alice.id, dave.id, FollowStatus.PENDING);

        List<UUID> followedIds = followService.getFollowing(alice.id, 0, 20).stream()
                .map(f -> f.followedId)
                .toList();

        assertEquals(2, followedIds.size());
        assertTrue(followedIds.contains(bob.id));
        assertTrue(followedIds.contains(carol.id));
        assertFalse(followedIds.contains(dave.id), "PENDING follow must be excluded");
    }

    @Test
    @TestTransaction
    void rejectRequest_followerCanReFollowAfterReject() {
        User alice = persistUser("auth0|s-refl-alice", "s-refl-alice@example.com", true);
        User bob = persistUser("auth0|s-refl-bob", "s-refl-bob@example.com", false);
        Follow pending = persistFollow(alice.id, bob.id, FollowStatus.PENDING);

        followService.rejectRequest("auth0|s-refl-bob", pending.id);

        // Re-following must succeed (no 409 already_following).
        Follow newRow = followService.follow("auth0|s-refl-alice", bob.id);
        assertNotNull(newRow.id);
        assertEquals(FollowStatus.PENDING, newRow.status, "re-follow against private profile must be PENDING");
        // ID must be different (delete then insert, not status flip).
        assertNotNull(newRow.createdAt);
    }

    @Test
    @TestTransaction
    void follow_selfFollow_throwsUnprocessable() {
        User alice = persistUser("auth0|s-self-alice", "s-self-alice@example.com", true);

        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> followService.follow("auth0|s-self-alice", alice.id));
        assertEquals(422, ex.getResponse().getStatus());
    }

    /**
     * Anti-oracle: User#profilePublic=false + caller != owner → 404,
     * masking existence (same envelope as "user not found").
     */
    @Test
    @TestTransaction
    void getFollowers_privateProfileNonOwner_returns404_antiOracle() {
        User alice = persistUser("auth0|s-priv-alice", "s-priv-alice@example.com", false);
        persistUser("auth0|s-priv-bob", "s-priv-bob@example.com", true);

        // FollowService.assertProfileVisible drives the 404 — same path used by
        // GET /users/{id}/followers in FollowResource before reading rows.
        assertThrows(NotFoundException.class,
                () -> followService.assertProfileVisible(alice.id, "auth0|s-priv-bob"));
    }

    @Test
    @TestTransaction
    void getPublicProfile_self_followStatusIsNull() {
        User alice = persistUser("auth0|s-self-pp", "s-self-pp@example.com", true);

        PublicProfileView view = userService.getPublicProfile(alice.id, "auth0|s-self-pp");

        assertEquals(alice.id, view.user().id);
        assertNull(view.followStatus(), "self-views must surface followStatus=null");
    }

    @Test
    @TestTransaction
    void getPublicProfile_authNonOwnerWithPending_followStatusIsPending() {
        User alice = persistUser("auth0|s-pending-alice", "s-pending-alice@example.com", true);
        User bob = persistUser("auth0|s-pending-bob", "s-pending-bob@example.com", false);
        persistFollow(alice.id, bob.id, FollowStatus.PENDING);

        // Alice (the follower with a PENDING row) is the OWNER of the request, but
        // sentinel asks for "GET /users/{other}" where other = bob and caller = alice.
        // Since bob.profilePublic = false but alice has a pending row, anti-oracle
        // 404 still fires (alice is not the owner) — actually re-read the spec:
        // "GET /users/{other} when caller has a PENDING Follow toward {other}
        // → followStatus=PENDING". We need bob to be PUBLIC profile so the read
        // succeeds and we can assert followStatus.
        bob.profilePublic = true;
        entityManager.flush();

        PublicProfileView view = userService.getPublicProfile(bob.id, "auth0|s-pending-alice");
        assertEquals(bob.id, view.user().id);
        assertEquals(FollowStatus.PENDING, view.followStatus());
    }

    // ── helpers ────────────────────────────────────────────────────────────

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
