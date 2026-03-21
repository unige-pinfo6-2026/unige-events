package ch.unige.events.dto;

import ch.unige.events.entity.User;

import java.util.List;
import java.util.UUID;

public record UserPublicResponse(
    UUID id,
    String displayName,
    String faculty,
    String studyLevel,
    String bio,
    List<String> interests,
    String avatarUrl
) {
    public static UserPublicResponse from(User u) {
        return new UserPublicResponse(
            u.getId(), u.getDisplayName(), u.getFaculty(),
            u.getStudyLevel(), u.getBio(), u.getInterests(), u.getAvatarUrl()
        );
    }
}
