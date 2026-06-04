package ch.unige.events.user.follow.entity;

import ch.unige.events.shared.domain.enums.FollowStatus;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FollowTest {

    @Test
    void fields_areAssignableAndReadable() {
        Follow f = new Follow();
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0);

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
        LocalDateTime fixed = LocalDateTime.of(2026, Month.JANUARY, 1, 12, 0);
        f.createdAt = fixed;
        f.prePersist();
        assertEquals(fixed, f.createdAt);
    }
}
