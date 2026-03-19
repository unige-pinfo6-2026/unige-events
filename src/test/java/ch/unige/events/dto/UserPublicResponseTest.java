package ch.unige.events.dto;

import ch.unige.events.entity.User;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class UserPublicResponseTest {

    @Test
    void fromMapsPublicFields() {
        User user = new User();
        UUID id = UUID.randomUUID();

        user.setId(id);
        user.setDisplayName("Alice");
        user.setFaculty("Science");
        user.setStudyLevel("Bachelor");
        user.setBio("Student at UNIGE");
        user.setInterests("AI, football");
        user.setAvatarUrl("https://cdn.example.com/avatar.png");

        UserPublicResponse response = UserPublicResponse.from(user);

        assertEquals(id, response.id());
        assertEquals("Alice", response.displayName());
        assertEquals("Science", response.faculty());
        assertEquals("Bachelor", response.studyLevel());
        assertEquals("Student at UNIGE", response.bio());
        assertEquals("AI, football", response.interests());
        assertEquals("https://cdn.example.com/avatar.png", response.avatarUrl());
    }
}
