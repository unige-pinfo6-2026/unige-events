package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.domain.enums.FollowStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserPublicResponseTest {

    @Test
    void anonymous_keepsIdDisplayNameAvatar_stripsRest() {
        UUID id = UUID.randomUUID();
        UserPublicResponse r = UserPublicResponse.anonymous(id, "Alice", "https://cdn/a.png");

        assertEquals(id, r.id());
        assertEquals("Alice", r.displayName());
        assertEquals("https://cdn/a.png", r.avatarUrl());
        assertNull(r.faculty());
        assertNull(r.studyLevel());
        assertNull(r.bio());
        assertNull(r.interests());
        assertNull(r.bannerUrl());
        assertEquals(0L, r.followerCount());
        assertEquals(0L, r.followingCount());
        assertNull(r.followStatus());
    }

    @Test
    void fullRecord_canBeConstructedDirectly() {
        UUID id = UUID.randomUUID();
        UserPublicResponse r = new UserPublicResponse(
                id, "Bob", Faculty.MEDICINE, "MASTER",
                "Bio here", List.of("kayak"), "https://avatar/b", "https://banner/b",
                42L, 7L, FollowStatus.ACCEPTED);
        assertEquals(Faculty.MEDICINE, r.faculty());
        assertEquals(42L, r.followerCount());
        assertEquals(FollowStatus.ACCEPTED, r.followStatus());
    }

    @Test
    void faculty_serialization_usesCanonicalEnumName() {
        // Sentinel: Jackson default serialization renders enums via name() —
        // i.e. canonical UPPERCASE values. Drift here would break the
        // wire format (lowercase variants are explicitly out of contract).
        for (Faculty f : Faculty.values()) {
            assertEquals(f.name(), f.toString());
            assertEquals(f.name().toUpperCase(java.util.Locale.ROOT), f.name());
        }
    }
}
