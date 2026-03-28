package ch.unige.events.entity;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class UserTest {

    @Test
    void defaultValuesAndPublicFieldsWork() {
        User user = new User();

        assertFalse(user.profilePublic);
        assertNotNull(user.createdAt);
        assertEquals(0L, user.version);

        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 19, 12, 0, 0);

        user.id = id;
        user.auth0Id = "auth0|alice";
        user.email = "alice@example.com";
        user.displayName = "Alice";
        user.faculty = "Science";
        user.studyLevel = "Bachelor";
        user.bio = "Student at UNIGE";
        user.interests = List.of("AI, football");
        user.avatarUrl = "https://cdn.example.com/avatar.png";
        user.profilePublic = true;
        user.createdAt = createdAt;
        user.version = 7L;

        assertEquals(id, user.id);
        assertEquals("auth0|alice", user.auth0Id);
        assertEquals("alice@example.com", user.email);
        assertEquals("Alice", user.displayName);
        assertEquals("Science", user.faculty);
        assertEquals("Bachelor", user.studyLevel);
        assertEquals("Student at UNIGE", user.bio);
        assertEquals(List.of("AI, football"), user.interests);
        assertEquals("https://cdn.example.com/avatar.png", user.avatarUrl);
        assertTrue(user.profilePublic);
        assertEquals(createdAt, user.createdAt);
        assertEquals(7L, user.version);
    }
}
