package ch.unige.events.shared.kafka.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of the {@code co-organizers.{invited,accepted}} Kafka
 * topics. Single record discriminated by {@link Type}.
 *
 * <ul>
 * <li>{@code INVITED} — partition key = {@code userId} (the invitee
 *     gets a notification).</li>
 * <li>{@code ACCEPTED} — partition key = {@code eventId} (the event
 *     creator + future projection consumers care about all accepts on
 *     a given event).</li>
 * </ul>
 */
public record CoOrganizerEvent(
        Type type,
        long eventId,
        UUID userId,
        Instant occurredAt
) {

    public enum Type {
        INVITED,
        ACCEPTED
    }

    public static CoOrganizerEvent invited(long eventId, UUID userId) {
        return new CoOrganizerEvent(Type.INVITED, eventId, userId, Instant.now());
    }

    public static CoOrganizerEvent accepted(long eventId, UUID userId) {
        return new CoOrganizerEvent(Type.ACCEPTED, eventId, userId, Instant.now());
    }
}
