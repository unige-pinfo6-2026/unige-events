package ch.unige.events.dto;

import ch.unige.events.entity.User;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class UserProfileResponseTest {

    @Test
    void fromUserMapsAllFields() {
        User user = new User();
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 3, 19, 13, 15, 0);

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

        UserProfileResponse response = UserProfileResponse.fromUser(user);

        assertEquals(id, response.id());
        assertEquals("auth0|alice", response.auth0Id());
        assertEquals("alice@example.com", response.email());
        assertEquals("Alice", response.displayName());
        assertEquals("Science", response.faculty());
        assertEquals("Bachelor", response.studyLevel());
        assertEquals("Student at UNIGE", response.bio());
        assertEquals(List.of("AI, football"), response.interests());
        assertEquals("https://cdn.example.com/avatar.png", response.avatarUrl());
        assertTrue(response.profilePublic());
        assertEquals(createdAt, response.createdAt());
    }
}
