package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.domain.enums.FollowStatus;

import java.util.List;
import java.util.UUID;

/**
 * Public projection of a user — produced by user-service, consumed by
 * event-service (organizer enrichment) and engagement-service (comment
 * author + attendance display).
 *
 * <p>The {@code followerCount} / {@code followingCount} /
 * {@code followStatus} fields are populated only when the request is
 * authenticated and the caller is allowed to see them ; otherwise the
 * record carries default zeros + null.
 *
 * <p>{@code studyLevel} stays free-text until a {@code StudyLevel} enum
 * is introduced (deferred to S9+). Expected canonical values:
 * {@code LICENCE}, {@code MASTER}, {@code PHD}, {@code OTHER}.
 */
public record UserPublicResponse(
        UUID id,
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

    /**
     * Anonymous projection — keeps id + displayName + avatarUrl,
     * strips every other field. Used when the caller is unauthenticated
     * and the target's profile is public.
     */
    public static UserPublicResponse anonymous(UUID id, String displayName, String avatarUrl) {
        return new UserPublicResponse(id, displayName, null, null, null, null, avatarUrl, null, 0L, 0L, null);
    }
}
