package ch.unige.events.user.follow.dto;

import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.follow.entity.Follow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FollowDTOTest {

    @Test
    void canonicalConstructor_populatedFields_keepsAllValues() {
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        LocalDateTime t = LocalDateTime.of(2026, Month.MAY, 1, 12, 0);

        FollowDTO dto = new FollowDTO(42L, follower, followed, FollowStatus.ACCEPTED, t);

        assertEquals(42L, dto.id());
        assertEquals(follower, dto.followerId());
        assertEquals(followed, dto.followedId());
        assertEquals(FollowStatus.ACCEPTED, dto.status());
        assertEquals(t, dto.createdAt());
    }

    @Test
    void from_followEntity_copiesAllFields() {
        Follow f = new Follow();
        f.id = 11L;
        f.followerId = UUID.randomUUID();
        f.followedId = UUID.randomUUID();
        f.status = FollowStatus.PENDING;
        f.createdAt = LocalDateTime.of(2026, Month.APRIL, 30, 9, 30);

        FollowDTO dto = FollowDTO.from(f);

        assertEquals(11L, dto.id());
        assertEquals(f.followerId, dto.followerId());
        assertEquals(f.followedId, dto.followedId());
        assertEquals(FollowStatus.PENDING, dto.status());
        assertEquals(f.createdAt, dto.createdAt());
    }

    @Test
    void from_unsavedEntity_carriesNullId() {
        Follow f = new Follow();
        f.followerId = UUID.randomUUID();
        f.followedId = UUID.randomUUID();
        f.status = FollowStatus.ACCEPTED;
        f.createdAt = LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0);

        FollowDTO dto = FollowDTO.from(f);

        assertNull(dto.id());
        assertEquals(FollowStatus.ACCEPTED, dto.status());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        LocalDateTime t = LocalDateTime.of(2026, Month.JANUARY, 1, 0, 0);

        FollowDTO a = new FollowDTO(1L, follower, followed, FollowStatus.ACCEPTED, t);
        FollowDTO b = new FollowDTO(1L, follower, followed, FollowStatus.ACCEPTED, t);
        FollowDTO c = new FollowDTO(2L, follower, followed, FollowStatus.ACCEPTED, t);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
