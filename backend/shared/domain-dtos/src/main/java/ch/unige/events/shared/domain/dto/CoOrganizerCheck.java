package ch.unige.events.shared.domain.dto;

/**
 * Single-bit projection: is {@code userId} an ACCEPTED co-organizer of
 * {@code eventId}? Returned by event-service's internal
 * {@code GET /events/{eventId}/co-organizers/check?userId=}. Produced by
 * event-service ; consumed by engagement-service (CommentService +
 * ReportService cascade SCRUM-136 check).
 */
public record CoOrganizerCheck(boolean accepted) {

    public static CoOrganizerCheck yes() {
        return new CoOrganizerCheck(true);
    }

    public static CoOrganizerCheck no() {
        return new CoOrganizerCheck(false);
    }
}
