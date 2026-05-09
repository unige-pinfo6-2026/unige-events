package ch.unige.events.shared.kafka.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of the {@code events.banned} Kafka topic. Emitted by
 * report-service when an admin BAN decision lands or when
 * ModerationCleanupJob auto-bans an event past the threshold.
 * Consumed by event-service which applies {@code event.status = BANNED}
 * locally (idempotent).
 *
 * <p>{@code bannedBy} may be null for the auto-ban path
 * (ModerationCleanupJob fires without a human admin).
 */
public record EventBannedEvent(
        long eventId,
        UUID bannedBy,
        String reason,
        Instant bannedAt
) {

    public static EventBannedEvent banned(long eventId, UUID bannedBy, String reason) {
        return new EventBannedEvent(eventId, bannedBy, reason, Instant.now());
    }
}
