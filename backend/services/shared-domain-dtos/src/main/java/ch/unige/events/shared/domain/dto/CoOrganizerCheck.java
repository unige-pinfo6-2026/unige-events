package ch.unige.events.shared.domain.dto;

/**
 * Single-bit projection: is {@code userId} an ACCEPTED co-organizer of
 * {@code eventId}? Returned by event-service (co-located post-finalization)'s internal
 * {@code GET /events/{eventId}/co-organizers/check?userId=} — consumed
 * by event-service / comment-service / attendance-service /
 * moderation-service / stats-service to centralise the cascade SCRUM-136
 * authorization (Décision L of the completion spec).
 */
public record CoOrganizerCheck(boolean accepted) {

    public static CoOrganizerCheck yes() {
        return new CoOrganizerCheck(true);
    }

    public static CoOrganizerCheck no() {
        return new CoOrganizerCheck(false);
    }
}
