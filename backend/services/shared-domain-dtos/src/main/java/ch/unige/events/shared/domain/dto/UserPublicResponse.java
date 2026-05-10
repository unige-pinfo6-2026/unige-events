package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.FollowStatus;

import java.util.List;
import java.util.UUID;

/**
 * Cross-service projection of a user's public profile. user-service is
 * the only producer (via {@code GET /users/{id}}) ; consumed by every
 * other service that needs to enrich its own DTOs with a username +
 * avatar (engagement-service, user-service, engagement-service — all renamed/co-located post-finalization).
 *
 * <p>The {@code followerCount} / {@code followingCount} /
 * {@code followStatus} fields are populated only when the request is
 * authenticated and the caller is allowed to see them ; otherwise the
 * record carries default zeros + null.
 */
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

    /**
     * Anonymous projection — keeps id + displayName + avatarUrl,
     * strips every other field. Used when the caller is unauthenticated
     * and the target's profile is public.
     */
    public static UserPublicResponse anonymous(UUID id, String displayName, String avatarUrl) {
        return new UserPublicResponse(id, displayName, null, null, null, null, avatarUrl, null, 0L, 0L, null);
    }
}
