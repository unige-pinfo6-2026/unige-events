package ch.unige.events.dto.user;

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
    String avatarUrl,
    String bannerUrl
) {
    public static UserPublicResponse from(User user) {
        return new UserPublicResponse(
                user.id,
                user.displayName,
                user.faculty,
                user.studyLevel,
                user.bio,
                user.interests,
                user.avatarUrl,
                user.bannerUrl
        );
    }

    /**
     * Factory for anonymous callers — projects only id, displayName and avatarUrl;
     * other fields are null. Hotfix pentest 2026-04-17 finding 4.1b (limit anonymous
     * harvest of opt-in public profiles).
     */
    public static UserPublicResponse fromAnonymous(User user) {
        return new UserPublicResponse(
                user.id,
                user.displayName,
                null,
                null,
                null,
                null,
                user.avatarUrl,
                null
        );
    }
}
