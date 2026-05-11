package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.FollowStatus;

/**
 * Internal projection of a user's follow graph. Returned by
 * user-service's {@code GET /users/{id}/follow-counts}
 * (follow-service was absorbed into user-service post-finalization,
 * cf. sprint-context.md § Étape 23) — consumed by user-service to
 * enrich {@code GET /users/{id}} payloads.
 *
 * <p>{@code followStatus} reflects the caller's relationship to the
 * target (PENDING / ACCEPTED / null = not following / self).
 */
public record FollowCounts(
        long followers,
        long following,
        FollowStatus followStatus
) {

    public static FollowCounts empty() {
        return new FollowCounts(0L, 0L, null);
    }
}
