package ch.unige.events.service;

import ch.unige.events.dto.CreateEventRequest;
import ch.unige.events.dto.EventDTO;
import ch.unige.events.entity.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class EventService {

    @Transactional
    public List<EventDTO> getAll() {
        return Event.<Event>listAll().stream().map(EventDTO::from).toList();
    }

    @Transactional
    public EventDTO create(CreateEventRequest request) {
        Event event = new Event();
        event.title = request.title;
        event.description = request.description;
        event.location = request.location;
        event.startDate = request.startDate;
        event.endDate = request.endDate;
        event.category = request.category;
        event.bannerUrl = request.bannerUrl;
        event.capacity = request.capacity;
        // creator: sera lié à l'utilisateur authentifié au Sprint 2
        event.persist();
        return EventDTO.from(event);
    }
}
