package ch.unige.events.shared.domain.dto;

/**
 * Internal projection of attendance counts on a single event. Returned
 * by attendance-service's {@code GET /events/{eventId}/attendance-summary}
 * — consumed by event-service (capacity gating), co-organizer-service
 * (display), stats-service (aggregation).
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
