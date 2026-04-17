package ch.unige.events.service;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventView;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.UUID;

@ApplicationScoped
public class EventViewService {

    @Transactional
    public void recordView(String auth0Id, Long eventId) {
        Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UUID userId = User.<User>find("auth0Id", auth0Id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User profile not found"))
                .id;

        // Upsert: ignore if already viewed
        boolean alreadyViewed = EventView.findByEventAndUser(eventId, userId).isPresent();
        if (alreadyViewed) {
            return;
        }

        EventView view = new EventView();
        view.eventId = eventId;
        view.userId = userId;
        view.persist();
    }
}
