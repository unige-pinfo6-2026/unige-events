package ch.unige.events.service;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.UUID;

@ApplicationScoped
public class EventViewService {

    @Inject
    EntityManager entityManager;

    @Transactional
    public void recordView(String auth0Id, Long eventId) {
        Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UUID userId = User.<User>find("auth0Id", auth0Id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User profile not found"))
                .id;

        // INSERT ... ON CONFLICT DO NOTHING — atomique au niveau DB.
        // Élimine la race condition du check-then-insert : deux requêtes concurrentes
        // pour le même (eventId, userId) ne peuvent pas toutes les deux insérer ;
        // la contrainte unique garantit qu'une seule ligne est créée, l'autre est ignorée
        // silencieusement sans lever d'exception côté applicatif.
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
