package ch.unige.events.user.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    @Test
    void defaultValues_initialState() {
        User user = new User();

        assertFalse(user.profilePublic);
        assertNotNull(user.createdAt);
        assertEquals(0L, user.version);
    }

    @Test
    void publicFields_areAssignableAndReadable() {
        User user = new User();
        UUID id = UUID.randomUUID();
        UUID calendarToken = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 19, 12, 0, 0);

        user.id = id;
        user.auth0Id = "auth0|alice";
        user.email = "alice@example.com";
        user.displayName = "Alice";
        user.firstName = "First";
        user.lastName = "Last";
        user.faculty = "Science";
        user.studyLevel = "Bachelor";
        user.bio = "Student at UNIGE";
        user.interests = List.of("AI", "Football");
        user.avatarUrl = "https://cdn.example.com/avatar.png";
        user.bannerUrl = "https://cdn.example.com/banner.png";
        user.profilePublic = true;
        user.createdAt = createdAt;
        user.version = 7L;
        user.calendarToken = calendarToken;

        assertEquals(id, user.id);
        assertEquals("auth0|alice", user.auth0Id);
        assertEquals("alice@example.com", user.email);
        assertEquals("Alice", user.displayName);
        assertEquals("First", user.firstName);
        assertEquals("Last", user.lastName);
        assertEquals("Science", user.faculty);
        assertEquals("Bachelor", user.studyLevel);
        assertEquals("Student at UNIGE", user.bio);
        assertEquals(List.of("AI", "Football"), user.interests);
        assertEquals("https://cdn.example.com/avatar.png", user.avatarUrl);
        assertEquals("https://cdn.example.com/banner.png", user.bannerUrl);
        assertTrue(user.profilePublic);
        assertEquals(createdAt, user.createdAt);
        assertEquals(7L, user.version);
        assertEquals(calendarToken, user.calendarToken);
    }
}
