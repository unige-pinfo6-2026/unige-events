package ch.unige.events.user.dto;

import ch.unige.events.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProfileResponseTest {

    @Test
    void canonicalConstructor_populatedFields_keepsAllValues() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 5, 1, 10, 0);
        List<String> interests = List.of("hiking", "music");
        UserProfileResponse dto = new UserProfileResponse(
            id, "auth0|abc", "alice@unige.ch", "Alice", "Sciences", "Master",
            "Hi there", interests, "https://avatars/a.png", "https://banners/a.png",
            true, createdAt);

        assertEquals(id, dto.id());
        assertEquals("auth0|abc", dto.auth0Id());
        assertEquals("alice@unige.ch", dto.email());
        assertEquals("Alice", dto.displayName());
        assertEquals("Sciences", dto.faculty());
        assertEquals("Master", dto.studyLevel());
        assertEquals("Hi there", dto.bio());
        assertEquals(interests, dto.interests());
        assertEquals("https://avatars/a.png", dto.avatarUrl());
        assertEquals("https://banners/a.png", dto.bannerUrl());
        assertTrue(dto.profilePublic());
        assertEquals(createdAt, dto.createdAt());
    }

    @Test
    void from_userEntity_copiesAllFields() {
        User user = new User();
        user.id = UUID.randomUUID();
        user.auth0Id = "auth0|xyz";
        user.email = "bob@unige.ch";
        user.displayName = "Bob";
        user.faculty = "Lettres";
        user.studyLevel = "Bachelor";
        user.bio = null;
        user.interests = null;
        user.avatarUrl = null;
        user.bannerUrl = null;
        user.profilePublic = false;
        user.createdAt = LocalDateTime.of(2025, 12, 31, 23, 59);

        UserProfileResponse dto = UserProfileResponse.from(user);

        assertEquals(user.id, dto.id());
        assertEquals("auth0|xyz", dto.auth0Id());
        assertEquals("bob@unige.ch", dto.email());
        assertEquals("Bob", dto.displayName());
        assertEquals("Lettres", dto.faculty());
        assertEquals("Bachelor", dto.studyLevel());
        assertNull(dto.bio());
        assertNull(dto.interests());
        assertFalse(dto.profilePublic());
        assertEquals(user.createdAt, dto.createdAt());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID id = UUID.randomUUID();
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 0, 0);
        UserProfileResponse a = new UserProfileResponse(
            id, "a", "e", "d", "f", "s", "b", List.of(), "av", "bn", true, t);
        UserProfileResponse b = new UserProfileResponse(
            id, "a", "e", "d", "f", "s", "b", List.of(), "av", "bn", true, t);
        UserProfileResponse c = new UserProfileResponse(
            id, "a", "e", "d", "f", "s", "b", List.of(), "av", "bn", false, t);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
