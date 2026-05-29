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
        List<String> roles = List.of("ADMIN");
        UserPublicResponse dto = new UserPublicResponse(
            id, "alice.martin", "Alice", Faculty.SCIENCES, "Master", "bio",
            interests, "https://av/a.png", "https://bn/a.png", true,
            12L, 5L, FollowStatus.ACCEPTED, roles);

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
        assertEquals(roles, dto.roles());
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
    void fromRestricted_stripsPrivateFields_butKeepsFollowStatus() {
        // Authenticated non-owner non-admin viewer of a private profile —
        // private fields must be stripped (same as fromAnonymous) but the
        // real followStatus (PENDING or null) is preserved so the frontend
        // can render the correct FollowButton state without a separate call.
        User user = newUser(false);
        UserPublicResponse dtoPending = UserPublicResponse.fromRestricted(user, FollowStatus.PENDING);

        assertEquals(user.id, dtoPending.id());
        assertEquals(user.username, dtoPending.username());
        assertEquals(user.displayName, dtoPending.displayName());
        assertNull(dtoPending.faculty());
        assertNull(dtoPending.studyLevel());
        assertNull(dtoPending.bio());
        assertNull(dtoPending.interests());
        assertEquals(user.avatarUrl, dtoPending.avatarUrl());
        assertNull(dtoPending.bannerUrl());
        assertFalse(dtoPending.profilePublic());
        assertEquals(0L, dtoPending.followerCount());
        assertEquals(0L, dtoPending.followingCount());
        assertEquals(FollowStatus.PENDING, dtoPending.followStatus());

        // null followStatus (no relationship yet) — "Suivre" button.
        UserPublicResponse dtoNull = UserPublicResponse.fromRestricted(user, null);
        assertNull(dtoNull.followStatus());
    }

    @Test
    void from_userWithRoles_propagatesRolesAcrossAllProjections() {
        // Frontend reads `roles` to gate the Staff badge — must be present on
        // every projection path (full, with-counts, anonymous, restricted).
        User user = newUser(true);
        user.roles = List.of("ADMIN");
        assertEquals(List.of("ADMIN"), UserPublicResponse.from(user).roles());
        assertEquals(List.of("ADMIN"), UserPublicResponse.from(user, 0L, 0L, null).roles());
        assertEquals(List.of("ADMIN"), UserPublicResponse.fromAnonymous(user).roles());
        assertEquals(List.of("ADMIN"), UserPublicResponse.fromRestricted(user, null).roles());
    }

    @Test
    void from_userWithDefaultRoles_returnsEmptyListAcrossAllProjections() {
        // `User.roles` is initialised to an empty list on the entity ; the
        // DTO must propagate that as an empty list (never null) so the
        // frontend can call .includes() unconditionally.
        User user = newUser(true);
        // No explicit set — relies on the entity field initialiser.
        assertTrue(UserPublicResponse.from(user).roles().isEmpty());
        assertTrue(UserPublicResponse.from(user, 0L, 0L, null).roles().isEmpty());
        assertTrue(UserPublicResponse.fromAnonymous(user).roles().isEmpty());
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
            id, "x.handle", "X", Faculty.SCIENCES, "s", "b", List.of(), "av", "bn", true, 1L, 2L, FollowStatus.ACCEPTED, List.of());
        UserPublicResponse b = new UserPublicResponse(
            id, "x.handle", "X", Faculty.SCIENCES, "s", "b", List.of(), "av", "bn", true, 1L, 2L, FollowStatus.ACCEPTED, List.of());
        UserPublicResponse c = new UserPublicResponse(
            id, "x.handle", "X", Faculty.SCIENCES, "s", "b", List.of(), "av", "bn", true, 9L, 2L, FollowStatus.ACCEPTED, List.of());

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
