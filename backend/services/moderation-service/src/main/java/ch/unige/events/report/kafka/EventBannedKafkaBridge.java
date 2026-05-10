package ch.unige.events.report.kafka;

import ch.unige.events.shared.kafka.events.EventBannedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * CDI bridge from the in-process {@link EventBannedEvent} CDI event
 * (fired by {@code ReportService.handle} / {@code ModerationCleanupService})
 * into the transactional outbox (Decision K, ADR-003).
 *
 * <p>The observer runs in the firing thread's transaction (default
 * phase) so the outbox row commits atomically with the {@code Report}
 * status flip. This replaces the previous {@code AFTER_SUCCESS}
 * direct-emitter path, which silently lost bans on Kafka outages.
 */
@ApplicationScoped
public class EventBannedKafkaBridge {

    @Inject EventBannedPublisher publisher;

    void onBanned(@Observes EventBannedEvent ev) {
        publisher.persist(ev);
    }
}
