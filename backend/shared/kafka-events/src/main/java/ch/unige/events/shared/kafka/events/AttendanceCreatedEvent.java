package ch.unige.events.shared.kafka.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of the {@code attendances.created} Kafka topic — emitted by
 * engagement-service from {@code AttendanceService.attend} post-commit
 * <em>only</em> when the effective status is {@code ATTENDING} (cf.
 * SCRUM-99 Décision M — promotions WAITLISTED→ATTENDING do not re-emit).
 *
 * <p>Consumed by notification-service to push a {@code NEW_ATTENDEE}
 * notification to the event creator. Kept minimal — the consumer fetches
 * the full event payload (title, creatorId) via
 * {@code GET /events/{id}} on event-service to compose the message.
 */
public record AttendanceCreatedEvent(
        long attendanceId,
        long eventId,
        UUID userId,
        Instant occurredAt
) {

    public static AttendanceCreatedEvent of(long attendanceId, long eventId, UUID userId) {
        return new AttendanceCreatedEvent(attendanceId, eventId, userId, Instant.now());
    }
}
