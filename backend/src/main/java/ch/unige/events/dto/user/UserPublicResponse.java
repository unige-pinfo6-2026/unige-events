package ch.unige.events.dto.user;

import ch.unige.events.entity.User;

import java.util.List;
import java.util.UUID;

public record UserPublicResponse(
    UUID id,
    String username,
    String displayName,
    String faculty,
    String studyLevel,
    String bio,
    List<String> interests,
    String avatarUrl,
    String bannerUrl
) {
    public static UserPublicResponse from(User user) {
        return new UserPublicResponse(
                user.id,
                user.username,
                user.displayName,
                user.faculty,
                user.studyLevel,
                user.bio,
                user.interests,
                user.avatarUrl,
                user.bannerUrl
        );
    }

    public static UserPublicResponse fromAnonymous(User user) {
        return new UserPublicResponse(
                user.id,
                user.username,
                user.displayName,
                null, // faculty
                null, // studyLevel
                null, // bio
                null, // interests
                user.avatarUrl,
                null  // bannerUrl
        );
    }
}
