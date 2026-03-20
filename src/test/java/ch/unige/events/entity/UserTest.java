package ch.unige.events.entity;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class UserTest {

    @Test
    void defaultValuesAndAccessorsWork() {
        User user = new User();

        assertFalse(user.isProfilePublic());
        assertNotNull(user.getCreatedAt());
        assertEquals(0L, user.getVersion());

        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 19, 12, 0, 0);

        user.setId(id);
        user.setAuth0Id("auth0|alice");
        user.setEmail("alice@example.com");
        user.setDisplayName("Alice");
        user.setFaculty("Science");
        user.setStudyLevel("Bachelor");
        user.setBio("Student at UNIGE");
        user.setInterests("AI, football");
        user.setAvatarUrl("https://cdn.example.com/avatar.png");
        user.setProfilePublic(true);
        user.setCreatedAt(createdAt);
        user.setVersion(7L);

        assertEquals(id, user.getId());
        assertEquals("auth0|alice", user.getAuth0Id());
        assertEquals("alice@example.com", user.getEmail());
        assertEquals("Alice", user.getDisplayName());
        assertEquals("Science", user.getFaculty());
        assertEquals("Bachelor", user.getStudyLevel());
        assertEquals("Student at UNIGE", user.getBio());
        assertEquals("AI, football", user.getInterests());
        assertEquals("https://cdn.example.com/avatar.png", user.getAvatarUrl());
        assertTrue(user.isProfilePublic());
        assertEquals(createdAt, user.getCreatedAt());
        assertEquals(7L, user.getVersion());
    }
}
