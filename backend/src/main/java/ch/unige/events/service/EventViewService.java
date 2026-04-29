package ch.unige.events.service;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventView;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

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

        boolean alreadyViewed = EventView.count("eventId = ?1 AND userId = ?2", eventId, userId) > 0;
        if (!alreadyViewed) {
            EventView view = new EventView();
            view.eventId = eventId;
            view.userId = userId;
            view.persist();
        }
    }
}
