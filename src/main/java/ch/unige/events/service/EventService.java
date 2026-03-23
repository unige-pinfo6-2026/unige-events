package ch.unige.events.service;

import ch.unige.events.dto.CreateEventRequest;
import ch.unige.events.dto.EventDTO;
import ch.unige.events.dto.UpdateEventRequest;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class EventService {

    @Transactional
    public List<EventDTO> getAll(int page, int size, EventStatus status, EventCategory category, UUID organizerId) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        if (status != null) {
            conditions.add("status = :status");
            params.put("status", status);
        }
        if (category != null) {
            conditions.add("category = :category");
            params.put("category", category);
        }
        if (organizerId != null) {
            conditions.add("creator.id = :organizerId");
            params.put("organizerId", organizerId);
        }

        PanacheQuery<Event> query;
        if (conditions.isEmpty()) {
            query = Event.find("order by startDate, id");
        } else {
            query = Event.find(String.join(" AND ", conditions) + " order by startDate, id", params);
        }

        return query.page(page, size).list().stream().map(EventDTO::from).toList();
    }

    @Transactional
    public EventDTO create(String auth0Id, CreateEventRequest request) {
        User creator = User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        Event event = new Event();
        event.title = request.title;
        event.description = request.description;
        event.location = request.location;
        event.startDate = request.startDate;
        event.endDate = request.endDate;
        event.category = request.category;
        event.bannerUrl = request.bannerUrl;
        event.capacity = request.capacity;
        event.creator = creator;
        event.persist();
        return EventDTO.from(event);
    }

    @Transactional
    public EventDTO getById(Long id) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        return EventDTO.from(event);
    }

    @Transactional
    public EventDTO update(Long id, String auth0Id, UpdateEventRequest request) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        if (!isCreator(event, auth0Id)) {
            throw new ForbiddenException("Only the event creator can update this event");
        }

        event.title = request.title;
        event.description = request.description;
        event.location = request.location;
        event.startDate = request.startDate;
        event.endDate = request.endDate;
        event.category = request.category;
        event.bannerUrl = request.bannerUrl;
        event.capacity = request.capacity;
        if (request.status != null) {
            event.status = request.status;
        }

        return EventDTO.from(event);
    }

    @Transactional
    public void delete(Long id, String auth0Id) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        if (!isCreator(event, auth0Id)) {
            throw new ForbiddenException("Only the event creator can cancel this event");
        }

        event.status = EventStatus.CANCELLED;
    }

    private static boolean isCreator(Event event, String auth0Id) {
        return event.creator != null
                && event.creator.auth0Id != null
                && event.creator.auth0Id.equals(auth0Id);
    }
}
