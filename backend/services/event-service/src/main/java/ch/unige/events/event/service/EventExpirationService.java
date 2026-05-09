package ch.unige.events.event.service;

import ch.unige.events.event.entity.Event;
import ch.unige.events.event.entity.EventStatus;
import ch.unige.events.shared.kafka.events.EventLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EventExpirationService {

    @Inject EntityManager entityManager;
    @Inject jakarta.enterprise.event.Event<EventLifecycleEvent> lifecycleEvent;

    /**
     * Walk every event still in {@code PUBLISHED} whose {@code endDate}
     * has elapsed, flip its status to {@code EXPIRED}, and emit a Kafka
     * notification on {@code events.expired} so downstream consumers
     * (notification-service when it lands, the dashboard, etc.) can react.
     *
     * <p>Iterating row-by-row is more verbose than the previous bulk
     * UPDATE but lets us emit one Kafka event per transition without a
     * second SELECT to recover the IDs ; the cron runs hourly with
     * typically &lt; 100 candidates so the perf hit is negligible.
     *
     * <p>Étape 3.4 finalization-ultimate (STUB-001 / Décision F): the
     * {@code JOIN FETCH e.creator} is gone — {@code Event.creatorId} is
     * now an id-only {@code UUID} column, no lazy proxy.
     */
    @Transactional
    public int expireEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<Event> candidates = entityManager.createQuery(
                "SELECT e FROM Event e " +
                "WHERE e.status = :published AND e.endDate < :now",
                Event.class)
                .setParameter("published", EventStatus.PUBLISHED)
                .setParameter("now", now)
                .getResultList();
        for (Event e : candidates) {
            e.status = EventStatus.EXPIRED;
            e.updatedAt = now;
            UUID creatorId = e.creatorId;
            // CDI fire — bridge publishes to Kafka AFTER_SUCCESS (Décision A).
            lifecycleEvent.fire(EventLifecycleEvent.expired(e.id, creatorId));
        }
        return candidates.size();
    }
}
