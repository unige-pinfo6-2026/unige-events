package ch.unige.events.user.dto;

import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPublicResponseTest {

    @Test
    void canonicalConstructor_populatedFields_keepsAllValues() {
        UUID id = UUID.randomUUID();
        List<String> interests = List.of("art");
        UserPublicResponse dto = new UserPublicResponse(
            id, "alice.martin", "Alice", Faculty.SCIENCES, "Master", "bio",
            interests, "https://av/a.png", "https://bn/a.png", true,
            12L, 5L, FollowStatus.ACCEPTED);

        assertEquals(id, dto.id());
        assertEquals("alice.martin", dto.username());
        assertEquals("Alice", dto.displayName());
        assertEquals(Faculty.SCIENCES, dto.faculty());
        assertEquals("Master", dto.studyLevel());
        assertEquals("bio", dto.bio());
        assertEquals(interests, dto.interests());
        assertEquals("https://av/a.png", dto.avatarUrl());
        assertEquals("https://bn/a.png", dto.bannerUrl());
        assertTrue(dto.profilePublic());
        assertEquals(12L, dto.followerCount());
        assertEquals(5L, dto.followingCount());
        assertEquals(FollowStatus.ACCEPTED, dto.followStatus());
    }

    @Test
    void from_userOnly_zerosCountsAndNullStatus() {
        User user = newUser(true);
        UserPublicResponse dto = UserPublicResponse.from(user);

        assertEquals(user.id, dto.id());
        assertEquals(user.username, dto.username());
        assertEquals(user.displayName, dto.displayName());
        assertEquals(user.faculty, dto.faculty());
        assertEquals(user.studyLevel, dto.studyLevel());
        assertEquals(user.bio, dto.bio());
        assertEquals(user.interests, dto.interests());
        assertEquals(user.avatarUrl, dto.avatarUrl());
        assertEquals(user.bannerUrl, dto.bannerUrl());
        assertTrue(dto.profilePublic());
        assertEquals(0L, dto.followerCount());
        assertEquals(0L, dto.followingCount());
        assertNull(dto.followStatus());
    }

    @Test
    void from_withCountsAndStatus_propagatesValues() {
        User user = newUser(true);
        UserPublicResponse dto = UserPublicResponse.from(user, 7L, 3L, FollowStatus.PENDING);

        assertEquals(user.id, dto.id());
        assertEquals(user.username, dto.username());
        assertTrue(dto.profilePublic());
        assertEquals(7L, dto.followerCount());
        assertEquals(3L, dto.followingCount());
        assertEquals(FollowStatus.PENDING, dto.followStatus());
    }

    @Test
    void from_privateUser_propagatesProfilePublicFalse() {
        User user = newUser(false);
        UserPublicResponse dto = UserPublicResponse.from(user);
        assertFalse(dto.profilePublic());
    }

    @Test
    void fromAnonymous_keepsUsername_stripsSensitiveFields() {
        // SCRUM-169 Décision E — username MUST be exposed even to anonymous
        // callers because it is the public-facing identifier of the profile.
        // profilePublic mirrors the user's actual visibility setting (the
        // stripping is a read-permission concern, the flag is what the
        // frontend uses to decide whether to render the "private profile"
        // placeholder).
        User user = newUser(true);
        UserPublicResponse dto = UserPublicResponse.fromAnonymous(user);

        assertEquals(user.id, dto.id());
        assertEquals(user.username, dto.username());
        assertEquals(user.displayName, dto.displayName());
        assertNull(dto.faculty());
        assertNull(dto.studyLevel());
        assertNull(dto.bio());
        assertNull(dto.interests());
        assertEquals(user.avatarUrl, dto.avatarUrl());
        assertNull(dto.bannerUrl());
        assertTrue(dto.profilePublic());
        assertEquals(0L, dto.followerCount());
        assertEquals(0L, dto.followingCount());
        assertNull(dto.followStatus());
    }

    @Test
    void fromAnonymous_privateUser_reportsProfilePublicFalse() {
        User user = newUser(false);
        UserPublicResponse dto = UserPublicResponse.fromAnonymous(user);
        assertFalse(dto.profilePublic());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID id = UUID.randomUUID();
        UserPublicResponse a = new UserPublicResponse(
            id, "x.handle", "X", Faculty.SCIENCES, "s", "b", List.of(), "av", "bn", true, 1L, 2L, FollowStatus.ACCEPTED);
        UserPublicResponse b = new UserPublicResponse(
            id, "x.handle", "X", Faculty.SCIENCES, "s", "b", List.of(), "av", "bn", true, 1L, 2L, FollowStatus.ACCEPTED);
        UserPublicResponse c = new UserPublicResponse(
            id, "x.handle", "X", Faculty.SCIENCES, "s", "b", List.of(), "av", "bn", true, 9L, 2L, FollowStatus.ACCEPTED);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    private static User newUser(boolean profilePublic) {
        User u = new User();
        u.id = UUID.randomUUID();
        u.username = "bob.smith";
        u.displayName = "Bob";
        u.faculty = Faculty.LETTERS;
        u.studyLevel = "Bachelor";
        u.bio = "hello";
        u.interests = List.of("music");
        u.avatarUrl = "https://av/b.png";
        u.bannerUrl = "https://bn/b.png";
        u.profilePublic = profilePublic;
        return u;
    }
}
