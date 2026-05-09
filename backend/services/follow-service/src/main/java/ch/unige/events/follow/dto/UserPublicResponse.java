package ch.unige.events.follow.dto;

import ch.unige.events.follow.entity.FollowStatus;
import ch.unige.events.follow.entity.UserStub;

import java.util.List;
import java.util.UUID;

/**
 * Mirror of legacy {@code ch.unige.events.dto.user.UserPublicResponse}.
 * follow-service emits this shape on {@code GET /users/{id}/followers}
 * and {@code /following} to keep the OpenAPI contract invariant.
 *
 * <p>The {@code from(UserStub)} factory zeroes followerCount /
 * followingCount / followStatus — that's already the legacy behaviour
 * for list items (cf. SCRUM-138 décision 11) — full enrichment happens
 * only on {@code GET /users/{id}} which is owned by user-service (PR 12).
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

    public static UserPublicResponse from(UserStub user) {
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

    /**
     * Pentest 4.1b — anonymous-or-private projection : only id + displayName +
     * avatarUrl visible to non-self callers when target's profile is private.
     */
    public static UserPublicResponse fromAnonymous(UserStub user) {
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
