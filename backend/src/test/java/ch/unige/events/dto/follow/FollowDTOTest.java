package ch.unige.events.dto.follow;

import ch.unige.events.entity.Follow;
import ch.unige.events.entity.FollowStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FollowDTOTest {

    @Test
    void from_projectsAllFields() {
        Follow f = new Follow();
        f.id = 42L;
        f.followerId = UUID.randomUUID();
        f.followedId = UUID.randomUUID();
        f.status = FollowStatus.ACCEPTED;
        f.createdAt = LocalDateTime.of(2026, 5, 7, 10, 30);

        FollowDTO dto = FollowDTO.from(f);

        assertEquals(42L, dto.id());
        assertEquals(f.followerId, dto.followerId());
        assertEquals(f.followedId, dto.followedId());
        assertEquals(FollowStatus.ACCEPTED, dto.status());
        assertEquals(f.createdAt, dto.createdAt());
    }

    @Test
    void from_withPendingStatus_keepsStatus() {
        Follow f = new Follow();
        f.id = 1L;
        f.followerId = UUID.randomUUID();
        f.followedId = UUID.randomUUID();
        f.status = FollowStatus.PENDING;
        f.createdAt = LocalDateTime.now();

        FollowDTO dto = FollowDTO.from(f);

        assertEquals(FollowStatus.PENDING, dto.status());
        assertNotNull(dto.createdAt());
    }
}
