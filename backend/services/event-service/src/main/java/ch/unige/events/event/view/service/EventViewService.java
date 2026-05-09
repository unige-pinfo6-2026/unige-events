package ch.unige.events.event.view.service;

import ch.unige.events.event.entity.Event;
import ch.unige.events.event.entity.UserStub;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Idempotent recording of "user X viewed event Y" — uses an upsert via
 * native SQL with ON CONFLICT, identical semantics to the legacy
 * monolith's EventViewService. Refreshing viewed_at gives "most recent
 * view" semantics.
 */
@ApplicationScoped
public class EventViewService {

    private final EntityManager entityManager;

    public EventViewService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void recordView(String auth0Id, Long eventId) {
        if (entityManager.find(Event.class, eventId) == null) {
            throw new NotFoundException("Event not found");
        }

        UUID userId = UserStub.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"))
                .id;

        // Native upsert: ON CONFLICT closes the concurrent-insert race.
        // event_views_seq is created by Hibernate's drop-and-create in test
        // and pre-existing in legacy-monolith's V5__create_event_views.sql
        // for prod.
        entityManager.createNativeQuery(
                "INSERT INTO event_views (id, event_id, user_id, viewed_at) " +
                "VALUES (nextval('event_views_seq'), :eventId, :userId, :viewedAt) " +
                "ON CONFLICT (event_id, user_id) DO UPDATE SET viewed_at = EXCLUDED.viewed_at")
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .setParameter("viewedAt", LocalDateTime.now())
                .executeUpdate();
    }
}
