package ch.unige.events.shared.domain.dto;

/**
 * Internal projection of attendance counts on a single event. Returned
 * by engagement-service's {@code GET /events/{eventId}/attendance-summary}
 * — produced by engagement-service ; consumed by event-service (capacity
 * gating + DTO enrichment).
 *
 * <p>Historical note: co-organizer-service / stats-service / comment-service / attendance-service were all absorbed post-Étape 2.X (cf. sprint-context.md § Étape 23).
 *
 * <p>Note: the {@code interested} count is always 0 in the current
 * model — INTERESTED is no longer a persisted attendance status. Kept
 * in the record for backwards compatibility in case it's reintroduced.
 */
public record AttendanceSummary(
        long attending,
        long waitlisted,
        long interested
) {

    public static AttendanceSummary of(long attending, long waitlisted) {
        return new AttendanceSummary(attending, waitlisted, 0L);
    }
}
