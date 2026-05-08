package ch.unige.events.service;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.UUID;

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

        UUID userId = User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"))
                .id;

        // Native upsert: ON CONFLICT closes the concurrent-insert race that the prior
        // check-then-insert pattern reintroduced (see commit 3591f37 → reverted by 2837585).
        // Refreshing viewed_at gives "most recent view" semantics. We call nextval explicitly
        // because long-lived databases bootstrapped by Hibernate-update (before Flyway adoption)
        // have the id column without DEFAULT — V5's CREATE TABLE IF NOT EXISTS was a no-op there.
        // The event_views_seq sequence is guaranteed to exist in both bootstrap paths.
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
