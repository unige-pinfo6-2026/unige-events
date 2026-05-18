package ch.unige.events.event.kafka;

import ch.unige.events.shared.kafka.events.EventLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/**
 * Bridges CDI-fired {@link EventLifecycleEvent}s to the Kafka
 * {@link EventLifecyclePublisher} <em>after</em> the JDBC transaction
 * commits (Décision A of the completion spec — fixes BUG-001 / BUG-002
 * fantôme events on rollback).
 *
 * <p>The {@link EventService} fires the CDI event from inside its
 * {@code @Transactional} method ; CDI delivers it to this observer
 * only if the transaction commits successfully. A rollback aborts the
 * delivery and no Kafka message is sent.
 */
@ApplicationScoped
public class EventLifecycleKafkaBridge {

    @Inject EventLifecyclePublisher publisher;

    void onAfterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) EventLifecycleEvent ev) {
        switch (ev.type()) {
            case PUBLISHED -> publisher.published(ev.eventId(), ev.creatorId());
            case CANCELLED -> publisher.cancelled(ev.eventId(), ev.creatorId());
            case EXPIRED -> publisher.expired(ev.eventId(), ev.creatorId());
            case UPDATED -> publisher.updated(ev.eventId(), ev.creatorId());
        }
    }
}
