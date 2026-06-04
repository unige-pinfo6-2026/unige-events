package ch.unige.events.shared.kafka.events;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of the {@code events.{published,cancelled,expired}} Kafka
 * topics — a single record so producer + consumer agree on the payload
 * regardless of which lifecycle transition fired it. The {@link Type}
 * discriminator lets a consumer fan out without subscribing to three
 * separate topics if it ever wants to.
 *
 * <p>Kept deliberately minimal — id + creator + timestamp. A consumer
 * that needs the full event payload can fetch it via
 * {@code GET /events/{id}} on event-service. Avoids embedding mutable
 * data (title, description) that would bit-rot in flight.
 */
public record EventLifecycleEvent(
        Type type,
        long eventId,
        UUID creatorId,
        Instant occurredAt
) {

    public enum Type {
        PUBLISHED,
        CANCELLED,
        EXPIRED,
        // SCRUM-99: emitted by EventService.update post-commit. Consumed by
        // notification-service to push EVENT_UPDATED in-app notifications to
        // attendees. The payload stays minimal (id + creator + timestamp) —
        // the consumer fetches the full Event via REST when it composes the
        // notification message.
        UPDATED
    }

    public static EventLifecycleEvent published(long eventId, UUID creatorId) {
        return new EventLifecycleEvent(Type.PUBLISHED, eventId, creatorId, Instant.now(Clock.systemUTC()));
    }

    public static EventLifecycleEvent cancelled(long eventId, UUID creatorId) {
        return new EventLifecycleEvent(Type.CANCELLED, eventId, creatorId, Instant.now(Clock.systemUTC()));
    }

    public static EventLifecycleEvent expired(long eventId, UUID creatorId) {
        return new EventLifecycleEvent(Type.EXPIRED, eventId, creatorId, Instant.now(Clock.systemUTC()));
    }

    public static EventLifecycleEvent updated(long eventId, UUID creatorId) {
        return new EventLifecycleEvent(Type.UPDATED, eventId, creatorId, Instant.now(Clock.systemUTC()));
    }
}
