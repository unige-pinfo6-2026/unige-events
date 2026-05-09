package ch.unige.events.user.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateProfileRequestTest {

    @Test
    void canonicalConstructor_populatedFields_keepsAllValues() {
        List<String> interests = List.of("hiking", "music");
        UpdateProfileRequest req = new UpdateProfileRequest(
            "Alice", "Sciences", "Master", "bio",
            interests, "https://avatars/a.png", true);

        assertEquals("Alice", req.displayName());
        assertEquals("Sciences", req.faculty());
        assertEquals("Master", req.studyLevel());
        assertEquals("bio", req.bio());
        assertEquals(interests, req.interests());
        assertEquals("https://avatars/a.png", req.avatarUrl());
        assertTrue(req.profilePublic());
    }

    @Test
    void canonicalConstructor_allNull_isAllowed() {
        UpdateProfileRequest req = new UpdateProfileRequest(
            null, null, null, null, null, null, null);

        assertNull(req.displayName());
        assertNull(req.faculty());
        assertNull(req.studyLevel());
        assertNull(req.bio());
        assertNull(req.interests());
        assertNull(req.avatarUrl());
        assertNull(req.profilePublic());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UpdateProfileRequest a = new UpdateProfileRequest(
            "X", "f", "s", "b", List.of("i"), "https://a", false);
        UpdateProfileRequest b = new UpdateProfileRequest(
            "X", "f", "s", "b", List.of("i"), "https://a", false);
        UpdateProfileRequest c = new UpdateProfileRequest(
            "Y", "f", "s", "b", List.of("i"), "https://a", false);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
