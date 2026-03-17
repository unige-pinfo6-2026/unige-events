package ch.unige.events.dto;

import ch.unige.events.entity.User;
import java.util.UUID;

public record UserPublicResponse(
    UUID id,
    String displayName,
    String faculty,
    String studyLevel,
    String bio,
    String interests,
    String avatarUrl
) {
    public static UserPublicResponse from(User u) {
        return new UserPublicResponse(
            u.id, u.displayName, u.faculty,
            u.studyLevel, u.bio, u.interests, u.avatarUrl
        );
    }
}
