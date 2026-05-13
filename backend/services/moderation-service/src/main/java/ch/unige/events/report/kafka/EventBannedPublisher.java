package ch.unige.events.report.kafka;

import ch.unige.events.report.outbox.EventBannedOutbox;
import ch.unige.events.shared.kafka.events.EventBannedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Persists an {@link EventBannedEvent} into the transactional outbox
 * (Decision K, ADR-003). The actual Kafka publication happens
 * asynchronously in {@code EventBannedOutboxPoller}.
 *
 * <p>Refactored from a direct {@code Emitter.send(...)} caller: a Kafka
 * outage when banning an event was previously a silent failure (event
 * stays publicly visible while moderation has
 * banned it). The outbox guarantees at-least-once delivery as long as
 * the DB transaction commits.
 */
@ApplicationScoped
public class EventBannedPublisher {

    @Inject ObjectMapper objectMapper;

    /**
     * Persists a new outbox row for the given event. Joins the surrounding
     * transaction (REQUIRED) — when called from the {@code @Transactional}
     * caller that fired the CDI event, the row commits atomically with the
     * moderation decision. Falls back to a fresh transaction when no caller
     * transaction is active (e.g., direct unit-test reflection).
     */
    @Transactional
    public void persist(EventBannedEvent ev) {
        EventBannedOutbox row = new EventBannedOutbox();
        row.eventId = ev.eventId();
        row.bannedBy = ev.bannedBy();
        row.occurredAt = ev.bannedAt();
        try {
            row.payloadJson = objectMapper.writeValueAsString(ev);
        } catch (JsonProcessingException e) {
            // Should never happen — EventBannedEvent is a record of primitives + UUID + Instant.
            // Re-throw so the surrounding @Transactional boundary rolls back the ban entirely.
            throw new IllegalStateException(
                    "Failed to serialize EventBannedEvent for outbox (eventId=" + ev.eventId() + ")", e);
        }
        row.attempts = 0;
        row.persist();
    }
}
