package ch.unige.events.user.dto;

import ch.unige.events.shared.domain.enums.Faculty;
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
        List<String> roles = List.of("ADMIN");
        UserProfileResponse dto = new UserProfileResponse(
            id, "auth0|abc", "alice@unige.ch", "alice.martin", "Alice", Faculty.SCIENCES, "Master",
            "Hi there", interests, "https://avatars/a.png", "https://banners/a.png",
            true, createdAt, roles);

        assertEquals(id, dto.id());
        assertEquals("auth0|abc", dto.auth0Id());
        assertEquals("alice@unige.ch", dto.email());
        assertEquals("alice.martin", dto.username());
        assertEquals("Alice", dto.displayName());
        assertEquals(Faculty.SCIENCES, dto.faculty());
        assertEquals("Master", dto.studyLevel());
        assertEquals("Hi there", dto.bio());
        assertEquals(interests, dto.interests());
        assertEquals("https://avatars/a.png", dto.avatarUrl());
        assertEquals("https://banners/a.png", dto.bannerUrl());
        assertTrue(dto.profilePublic());
        assertEquals(createdAt, dto.createdAt());
        assertEquals(roles, dto.roles());
    }

    @Test
    void from_userEntity_copiesAllFields() {
        User user = new User();
        user.id = UUID.randomUUID();
        user.auth0Id = "auth0|xyz";
        user.email = "bob@unige.ch";
        user.username = "bob.smith";
        user.displayName = "Bob";
        user.faculty = Faculty.LETTERS;
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
        assertEquals("bob.smith", dto.username());
        assertEquals("Bob", dto.displayName());
        assertEquals(Faculty.LETTERS, dto.faculty());
        assertEquals("Bachelor", dto.studyLevel());
        assertNull(dto.bio());
        assertNull(dto.interests());
        assertFalse(dto.profilePublic());
        assertEquals(user.createdAt, dto.createdAt());
        assertTrue(dto.roles().isEmpty());
    }

    @Test
    void from_userWithPopulatedRoles_propagatesRoles() {
        User user = new User();
        user.id = UUID.randomUUID();
        user.auth0Id = "auth0|admin";
        user.email = "admin@unige.ch";
        user.username = "admin.user";
        user.createdAt = LocalDateTime.now();
        user.roles = List.of("ADMIN", "MODERATOR");

        UserProfileResponse dto = UserProfileResponse.from(user);

        assertEquals(List.of("ADMIN", "MODERATOR"), dto.roles());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID id = UUID.randomUUID();
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 0, 0);
        UserProfileResponse a = new UserProfileResponse(
            id, "a", "e", "u", "d", Faculty.SCIENCES, "s", "b", List.of(), "av", "bn", true, t, List.of());
        UserProfileResponse b = new UserProfileResponse(
            id, "a", "e", "u", "d", Faculty.SCIENCES, "s", "b", List.of(), "av", "bn", true, t, List.of());
        UserProfileResponse c = new UserProfileResponse(
            id, "a", "e", "u", "d", Faculty.SCIENCES, "s", "b", List.of(), "av", "bn", false, t, List.of());

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
