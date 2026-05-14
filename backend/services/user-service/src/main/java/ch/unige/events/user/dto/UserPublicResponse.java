package ch.unige.events.user.dto;

import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.domain.enums.FollowStatus;
import ch.unige.events.user.entity.User;

import java.util.List;
import java.util.UUID;

public record UserPublicResponse(
    UUID id,
    String username,
    String displayName,
    Faculty faculty,
    String studyLevel,
    String bio,
    List<String> interests,
    String avatarUrl,
    String bannerUrl,
    long followerCount,
    long followingCount,
    FollowStatus followStatus
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
                user.bannerUrl,
                0L,
                0L,
                null
        );
    }

    public static UserPublicResponse from(
            User user,
            long followerCount,
            long followingCount,
            FollowStatus followStatus) {
        return new UserPublicResponse(
                user.id,
                user.username,
                user.displayName,
                user.faculty,
                user.studyLevel,
                user.bio,
                user.interests,
                user.avatarUrl,
                user.bannerUrl,
                followerCount,
                followingCount,
                followStatus
        );
    }

    /**
     * Anonymous projection — keeps id, username, displayName, avatarUrl,
     * nullifies the rest (cf. SCRUM-169 Décision E : {@code username} is
     * exposed even to anonymous callers since it is the public-facing
     * identifier of the profile).
     */
    public static UserPublicResponse fromAnonymous(User user) {
        return new UserPublicResponse(
                user.id,
                user.username,
                user.displayName,
                null,
                null,
                null,
                null,
                user.avatarUrl,
                null,
                0L,
                0L,
                null
        );
    }
}
