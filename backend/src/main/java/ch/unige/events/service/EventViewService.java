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

        entityManager.createNativeQuery(
                "INSERT INTO event_views (event_id, user_id, viewed_at) " +
                "VALUES (:eventId, :userId, :viewedAt) " +
                "ON CONFLICT (event_id, user_id) DO NOTHING")
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .setParameter("viewedAt", LocalDateTime.now())
                .executeUpdate();
    }
}
