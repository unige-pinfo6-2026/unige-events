package ch.unige.events.dto;

import ch.unige.events.entity.User;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class UserPublicResponseTest {

    @Test
    void fromUserMapsPublicFields() {
        User user = new User();
        UUID id = UUID.randomUUID();

        user.id = id;
        user.displayName = "Alice";
        user.faculty = "Science";
        user.studyLevel = "Bachelor";
        user.bio = "Student at UNIGE";
        user.interests = List.of("AI, football");
        user.avatarUrl = "https://cdn.example.com/avatar.png";

        UserPublicResponse response = UserPublicResponse.fromUser(user);

        assertEquals(id, response.id());
        assertEquals("Alice", response.displayName());
        assertEquals("Science", response.faculty());
        assertEquals("Bachelor", response.studyLevel());
        assertEquals("Student at UNIGE", response.bio());
        assertEquals(List.of("AI, football"), response.interests());
        assertEquals("https://cdn.example.com/avatar.png", response.avatarUrl());
    }
}
