package ch.unige.events.service;

import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.dto.event.UpdateEventRequest;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Faculty;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class EventService {

    @Inject FileStorageService fileStorageService;

    @Transactional
    public List<EventDTO> getAll(int page, int size, EventStatus status, EventCategory category, UUID organizerId, LocalDateTime endDateFrom, List<Faculty> faculties) {
        boolean filterFaculties = faculties != null && !faculties.isEmpty();

        StringBuilder jpql = new StringBuilder("SELECT DISTINCT e FROM Event e");
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        if (filterFaculties) {
            jpql.append(" JOIN e.faculties f");
            conditions.add("f IN :faculties");
            params.put("faculties", faculties);
        }
        if (status != null) {
            conditions.add("e.status = :status");
            params.put("status", status);
        }
        if (category != null) {
            conditions.add("e.category = :category");
            params.put("category", category);
        }
        if (organizerId != null) {
            conditions.add("e.creator.id = :organizerId");
            params.put("organizerId", organizerId);
        }
        if (endDateFrom != null) {
            conditions.add("e.endDate >= :endDateFrom");
            params.put("endDateFrom", endDateFrom);
        }

        if (!conditions.isEmpty()) {
            jpql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        jpql.append(" ORDER BY e.startDate, e.id");

        return Event.<Event>find(jpql.toString(), params)
                .page(page, size)
                .list()
                .stream()
                .map(EventDTO::from)
                .toList();
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
        event.faculties = request.faculties != null ? new ArrayList<>(request.faculties) : new ArrayList<>();
        event.bannerUrl = request.bannerUrl;
        event.capacity = request.capacity;
        event.creator = creator;
        if (request.getStatus() == EventStatus.CANCELLED) {
            throw new BadRequestException("CANCELLED is not a valid initial status");
        }
        event.status = request.getStatus() != null ? request.getStatus() : EventStatus.DRAFT;
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
        event.faculties = request.faculties != null ? new ArrayList<>(request.faculties) : new ArrayList<>();
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

    @Transactional
    public EventDTO publish(Long id, String auth0Id, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(id).orElseThrow(NotFoundException::new);

        if (!isAdmin && !isCreator(event, auth0Id)) {
            throw new ForbiddenException("Only the event creator or an admin can publish this event");
        }

        if (event.status != EventStatus.DRAFT) {
            String message = event.status == EventStatus.PUBLISHED
                    ? "Event is already published"
                    : "Event cannot be published: current status is " + event.status;
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(Map.of("error", "conflict", "message", message))
                            .build());
        }

        event.status = EventStatus.PUBLISHED;
        return EventDTO.from(event);
    }

    @Transactional
    public EventDTO uploadImage(Long id, String auth0Id, FileUpload fileUpload, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(id).orElseThrow(NotFoundException::new);

        if (!isAdmin && !isCreator(event, auth0Id)) {
            throw new ForbiddenException("Only the event creator or an admin can upload a banner");
        }

        event.bannerUrl = fileStorageService.saveImage(fileUpload);
        return EventDTO.from(event);
    }

    private static boolean isCreator(Event event, String auth0Id) {
        return event.creator != null
                && event.creator.auth0Id != null
                && event.creator.auth0Id.equals(auth0Id);
    }
}
