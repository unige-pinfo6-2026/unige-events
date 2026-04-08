package ch.unige.events.service;

import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.dto.event.UpdateEventRequest;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
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
    public List<EventDTO> getAll(int page, int size, EventStatus status, EventCategory category, UUID organizerId, LocalDateTime endDateFrom) {
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
        if (endDateFrom != null) {
            conditions.add("endDate >= :endDateFrom");
            params.put("endDateFrom", endDateFrom);
        }

        PanacheQuery<Event> query;
        if (conditions.isEmpty()) {
            query = Event.find("order by startDate, id");
        } else {
            query = Event.find(String.join(" AND ", conditions) + " order by startDate, id", params);
        }

        return query.page(page, size).list().stream()
                .map(e -> EventDTO.from(e, countAttending(e.id), countInterested(e.id)))
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
        event.bannerUrl = request.bannerUrl;
        event.capacity = request.capacity;
        event.creator = creator;
        if (request.getStatus() == EventStatus.CANCELLED) {
            throw new BadRequestException("CANCELLED is not a valid initial status");
        }
        event.status = request.getStatus() != null ? request.getStatus() : EventStatus.DRAFT;
        event.persist();
        // A newly created event has no attendances yet — counts are 0.
        return EventDTO.from(event, 0L, 0L);
    }

    @Transactional
    public EventDTO getById(Long id) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);
        return EventDTO.from(event, countAttending(id), countInterested(id));
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

        return EventDTO.from(event, countAttending(id), countInterested(id));
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
        return EventDTO.from(event, countAttending(id), countInterested(id));
    }

    @Transactional
    public EventDTO uploadImage(Long id, String auth0Id, FileUpload fileUpload, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(id).orElseThrow(NotFoundException::new);

        if (!isAdmin && !isCreator(event, auth0Id)) {
            throw new ForbiddenException("Only the event creator or an admin can upload a banner");
        }

        event.bannerUrl = fileStorageService.saveImage(fileUpload);
        return EventDTO.from(event, countAttending(id), countInterested(id));
    }

    private long countAttending(Long eventId) {
        return Attendance.count("eventId = ?1 and status = ?2", eventId, AttendanceStatus.ATTENDING);
    }

    private long countInterested(Long eventId) {
        return Attendance.count("eventId = ?1 and status = ?2", eventId, AttendanceStatus.INTERESTED);
    }

    private static boolean isCreator(Event event, String auth0Id) {
        return event.creator != null
                && event.creator.auth0Id != null
                && event.creator.auth0Id.equals(auth0Id);
    }
}
