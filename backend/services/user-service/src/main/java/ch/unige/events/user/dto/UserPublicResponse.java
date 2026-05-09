package ch.unige.events.user.dto;

import ch.unige.events.user.entity.FollowStatus;
import ch.unige.events.user.entity.User;

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
    String bannerUrl,
    long followerCount,
    long followingCount,
    FollowStatus followStatus
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

    public static UserPublicResponse fromAnonymous(User user) {
        return new UserPublicResponse(
                user.id,
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
