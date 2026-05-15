package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.shared.domain.enums.FollowStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)

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
        String username,
        String displayName,
        Faculty faculty,
        String studyLevel,
        String bio,
        List<String> interests,
        String avatarUrl,
        String bannerUrl,
        boolean profilePublic,
        long followerCount,
        long followingCount,
        FollowStatus followStatus
) {

    /**
     * Backward-compatible 12-arg constructor (pre-{@code profilePublic}) —
     * the new field defaults to {@code false}, the safe value for the
     * restricted projection.
     */
    public UserPublicResponse(
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
        this(id, username, displayName, faculty, studyLevel, bio, interests, avatarUrl, bannerUrl, false, followerCount, followingCount, followStatus);
    }

    /**
     * Backward-compatible 11-arg constructor (pre-SCRUM-169) — used by
     * cross-service test mocks that pre-date the username field. The new
     * field is left {@code null} ; do NOT use this constructor in production
     * code paths : producers (user-service) always populate {@code username}.
     */
    public UserPublicResponse(
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
        this(id, null, displayName, faculty, studyLevel, bio, interests, avatarUrl, bannerUrl, false, followerCount, followingCount, followStatus);
    }

    /**
     * Anonymous projection — keeps id, username, displayName and avatarUrl,
     * strips every other field. Used when the caller is unauthenticated and
     * the target's profile is public.
     *
     * <p>SCRUM-169 — {@code username} is exposed even to anonymous callers
     * because it is the public-facing identifier of the profile (used in the
     * {@code /profile/{username}} URL). Nullifying it would defeat the
     * primary use of the field.
     */
    public static UserPublicResponse anonymous(UUID id, String username, String displayName, String avatarUrl) {
        return new UserPublicResponse(id, username, displayName, null, null, null, null, avatarUrl, null, false, 0L, 0L, null);
    }

    /** Backward-compatible {@link #anonymous(UUID, String, String, String)} (pre-SCRUM-169). */
    public static UserPublicResponse anonymous(UUID id, String displayName, String avatarUrl) {
        return anonymous(id, null, displayName, avatarUrl);
    }
}
