package ch.unige.events.entity;

import ch.unige.events.service.ShareServiceCoverageProfile;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(ShareServiceCoverageProfile.class)
class FollowTest {

    @Inject
    EntityManager entityManager;

    @Test
    void fieldsAreAssignable() {
        Follow f = new Follow();
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        f.followerId = follower;
        f.followedId = followed;
        f.status = FollowStatus.PENDING;
        f.createdAt = now;

        assertEquals(follower, f.followerId);
        assertEquals(followed, f.followedId);
        assertEquals(FollowStatus.PENDING, f.status);
        assertEquals(now, f.createdAt);
    }

    @Test
    void prePersist_setsCreatedAt_whenNull() {
        Follow f = new Follow();
        f.prePersist();
        assertNotNull(f.createdAt);
    }

    @Test
    void prePersist_preservesExistingCreatedAt() {
        Follow f = new Follow();
        LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 12, 0);
        f.createdAt = fixed;
        f.prePersist();
        assertEquals(fixed, f.createdAt);
    }

    @Test
    @TestTransaction
    void persist_setsCreatedAtAutomatically() {
        UUID followerId = persistUser("auth0|f-persist-follower", "f-persist-follower@example.com").id;
        UUID followedId = persistUser("auth0|f-persist-followed", "f-persist-followed@example.com").id;

        Follow f = new Follow();
        f.followerId = followerId;
        f.followedId = followedId;
        f.status = FollowStatus.PENDING;
        f.persist();
        entityManager.flush();

        assertNotNull(f.id);
        assertNotNull(f.createdAt);
        assertTrue(f.createdAt.isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    @TestTransaction
    void uniqueConstraint_blocksDuplicateFollow() {
        UUID followerId = persistUser("auth0|f-uq-follower", "f-uq-follower@example.com").id;
        UUID followedId = persistUser("auth0|f-uq-followed", "f-uq-followed@example.com").id;

        Follow first = new Follow();
        first.followerId = followerId;
        first.followedId = followedId;
        first.status = FollowStatus.ACCEPTED;
        first.persist();
        entityManager.flush();

        Follow dup = new Follow();
        dup.followerId = followerId;
        dup.followedId = followedId;
        dup.status = FollowStatus.PENDING;

        assertThrows(PersistenceException.class, () -> {
            dup.persist();
            entityManager.flush();
        });
    }

    private User persistUser(String auth0Id, String email) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        user.profilePublic = true;
        user.createdAt = LocalDateTime.now();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }
}
