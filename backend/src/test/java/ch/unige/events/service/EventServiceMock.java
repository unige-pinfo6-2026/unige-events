package ch.unige.events.service;

import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.dto.event.UpdateEventRequest;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Faculty;
import ch.unige.events.entity.User;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Mock
@ApplicationScoped
public class EventServiceMock extends EventService {

    private final Map<Long, Event> eventsById = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);
    public static volatile boolean forceForbiddenOnUpdate = false;
    public static volatile boolean forceForbiddenOnDelete = false;
    public static volatile boolean forceConflictOnPublish = false;
    public static volatile boolean forceBadMimeOnUpload = false;
    public static volatile boolean forceMyEventsNotFound = false;
    public static volatile String myEventsExpectedAuth0Id = null;

    public void reset() {
        eventsById.clear();
        idSequence.set(1);
        forceForbiddenOnUpdate = false;
        forceForbiddenOnDelete = false;
        forceConflictOnPublish = false;
        forceBadMimeOnUpload = false;
        forceMyEventsNotFound = false;
        myEventsExpectedAuth0Id = null;
    }

    public Event seedEvent(String creatorAuth0Id, String title) {
        User creator = new User();
        creator.id = UUID.randomUUID();
        creator.auth0Id = creatorAuth0Id;

        Event event = new Event();
        event.id = idSequence.getAndIncrement();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = EventCategory.ACADEMIC;
        event.status = EventStatus.DRAFT;
        event.creator = creator;
        event.createdAt = LocalDateTime.now();
        event.updatedAt = LocalDateTime.now();

        eventsById.put(event.id, event);
        return event;
    }

    public Event seedEventWithStatus(String creatorAuth0Id, String title, EventStatus status, LocalDateTime createdAt) {
        Event event = seedEvent(creatorAuth0Id, title);
        event.status = status;
        event.createdAt = createdAt;
        return event;
    }

    @Override
    @SuppressWarnings("java:S107")
    public List<EventDTO> getAll(int page, int size, EventStatus status, EventCategory category, UUID organizerId, LocalDateTime endDateFrom, Faculty faculty, Boolean facultyNone, Boolean featured) {
        return eventsById.values().stream()
                .filter(e -> status == null || e.status == status)
                .filter(e -> category == null || e.category == category)
                .filter(e -> organizerId == null || (e.creator != null && organizerId.equals(e.creator.id)))
                .filter(e -> endDateFrom == null || (e.endDate != null && !e.endDate.isBefore(endDateFrom)))
                .filter(e -> {
                    // facultyNone=true has priority over faculty — mutually exclusive filter.
                    if (Boolean.TRUE.equals(facultyNone)) {
                        return e.faculty == null;
                    }
                    return faculty == null || e.faculty == faculty;
                })
                .filter(e -> !Boolean.TRUE.equals(featured) || e.featured)
                .map(e -> EventDTO.from(e, 0L, e.capacity == null ? null : (long) e.capacity, 0L, null, null))
                .toList();
    }

    @Override
    public EventDTO create(String auth0Id, CreateEventRequest request) {
        User creator = new User();
        creator.id = UUID.randomUUID();
        creator.auth0Id = auth0Id;

        Event event = new Event();
        event.id = idSequence.getAndIncrement();
        event.title = request.title;
        event.description = request.description;
        event.location = request.location;
        event.startDate = request.startDate;
        event.endDate = request.endDate;
        event.category = request.category;
        event.faculty = request.faculty;
        event.bannerUrl = request.bannerUrl;
        event.capacity = request.capacity;
        event.websiteUrl = request.websiteUrl;
        event.contactEmail = request.contactEmail;
        event.registrationDeadline = request.registrationDeadline;
        event.tags = EventService.normalizeTags(request.tags);
        event.status = EventStatus.DRAFT;
        event.creator = creator;
        event.createdAt = LocalDateTime.now();
        event.updatedAt = LocalDateTime.now();

        eventsById.put(event.id, event);
        return EventDTO.from(event, 0L, event.capacity == null ? null : (long) event.capacity, 0L, null, null);
    }

    @Override
    public EventDTO getById(Long id, String auth0Id, boolean isAdmin) {
        Event event = eventsById.get(id);
        if (event == null) {
            throw new NotFoundException();
        }
        // Même règle qu'en prod — hotfix pentest 4.12.
        boolean isCreator = event.creator != null
                && event.creator.auth0Id != null
                && event.creator.auth0Id.equals(auth0Id);
        if (event.status != EventStatus.PUBLISHED && !isAdmin && !isCreator) {
            throw new NotFoundException();
        }
        return EventDTO.from(event, 0L, event.capacity == null ? null : (long) event.capacity, 0L, null, null);
    }

    @Override
    public EventDTO update(Long id, String auth0Id, UpdateEventRequest request) {
        if (forceForbiddenOnUpdate) {
            throw new ForbiddenException("Forbidden");
        }
        Event event = eventsById.get(id);
        if (event == null) {
            throw new NotFoundException();
        }
        boolean isCreator = event.creator != null
                && event.creator.auth0Id != null
                && event.creator.auth0Id.equals(auth0Id);
        if (!isCreator) {
            throw new ForbiddenException("Only the event creator can update this event");
        }
        if (event.status == EventStatus.CANCELLED) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(Map.of("error", "conflict", "message", "Cancelled events cannot be modified"))
                            .build());
        }

        event.title = request.title;
        event.description = request.description;
        event.location = request.location;
        event.startDate = request.startDate;
        event.endDate = request.endDate;
        event.category = request.category;
        event.faculty = request.faculty;
        event.bannerUrl = request.bannerUrl;
        event.capacity = request.capacity;
        event.websiteUrl = request.websiteUrl;
        event.contactEmail = request.contactEmail;
        event.registrationDeadline = request.registrationDeadline;
        event.tags = EventService.normalizeTags(request.tags);
        if (request.status != null) {
            event.status = request.status;
        }
        event.updatedAt = LocalDateTime.now();

        return EventDTO.from(event, 0L, event.capacity == null ? null : (long) event.capacity, 0L, null, null);
    }

    @Override
    public void delete(Long id, String auth0Id) {
        if (forceForbiddenOnDelete) {
            throw new ForbiddenException("Forbidden");
        }
        Event event = eventsById.get(id);
        if (event == null) {
            throw new NotFoundException();
        }
        boolean isCreator = event.creator != null
                && event.creator.auth0Id != null
                && event.creator.auth0Id.equals(auth0Id);
        if (!isCreator) {
            throw new ForbiddenException("Only the event creator can delete this event");
        }
        if (event.status != EventStatus.CANCELLED) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(Map.of("error", "conflict", "message", "Only cancelled events can be permanently deleted"))
                            .build());
        }
        eventsById.remove(id);
    }

    @Override
    public EventDTO cancel(Long id, String auth0Id) {
        Event event = eventsById.get(id);
        if (event == null) {
            throw new NotFoundException();
        }
        boolean isCreator = event.creator != null
                && event.creator.auth0Id != null
                && event.creator.auth0Id.equals(auth0Id);
        if (!isCreator) {
            throw new ForbiddenException("Only the event creator can cancel this event");
        }
        if (event.status == EventStatus.CANCELLED) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(Map.of("error", "conflict", "message", "Event is already cancelled"))
                            .build());
        }
        event.status = EventStatus.CANCELLED;
        return EventDTO.from(event, 0L, event.capacity == null ? null : (long) event.capacity, 0L, null, null);
    }

    @Override
    public EventDTO restore(Long id, String auth0Id) {
        Event event = eventsById.get(id);
        if (event == null) {
            throw new NotFoundException();
        }
        boolean isCreator = event.creator != null
                && event.creator.auth0Id != null
                && event.creator.auth0Id.equals(auth0Id);
        if (!isCreator) {
            throw new ForbiddenException("Only the event creator can restore this event");
        }
        if (event.status != EventStatus.CANCELLED) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(Map.of("error", "conflict", "message", "Only cancelled events can be restored to draft"))
                            .build());
        }
        event.status = EventStatus.DRAFT;
        return EventDTO.from(event, 0L, event.capacity == null ? null : (long) event.capacity, 0L, null, null);
    }

    @Override
    public EventDTO publish(Long id, String auth0Id, boolean isAdmin) {
        if (forceConflictOnPublish) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(Map.of("error", "conflict", "message", "Event is already published"))
                            .build());
        }
        Event e = eventsById.get(id);
        if (e == null) throw new NotFoundException();
        if (forceForbiddenOnUpdate) throw new ForbiddenException("Forbidden");
        e.status = EventStatus.PUBLISHED;
        return EventDTO.from(e, 0L, e.capacity == null ? null : (long) e.capacity, 0L, null, null);
    }

    @Override
    public List<EventDTO> getMyEvents(String auth0Id, EventStatus status, int page, int size) {
        if (forceMyEventsNotFound) {
            throw new NotFoundException("User profile not found — call GET /users/me first");
        }
        return eventsById.values().stream()
                .filter(e -> e.creator != null && e.creator.auth0Id != null
                        && (myEventsExpectedAuth0Id == null
                                ? e.creator.auth0Id.equals(auth0Id)
                                : e.creator.auth0Id.equals(myEventsExpectedAuth0Id)))
                .filter(e -> status == null || e.status == status)
                .sorted((a, b) -> {
                    int cmp = b.createdAt.compareTo(a.createdAt);
                    if (cmp != 0) return cmp;
                    return Long.compare(b.id, a.id);
                })
                .map(e -> EventDTO.from(e, 0L, e.capacity == null ? null : (long) e.capacity, 0L, null, null))
                .toList();
    }

    @Override
    public EventDTO uploadImage(Long id, String auth0Id, FileUpload fileUpload, boolean isAdmin) {
        if (forceBadMimeOnUpload) {
            throw new BadRequestException("File must be an image");
        }
        Event e = eventsById.get(id);
        if (e == null) throw new NotFoundException();
        if (forceForbiddenOnUpdate) throw new ForbiddenException("Forbidden");
        e.bannerUrl = "/uploads/test-banner.jpg";
        return EventDTO.from(e, 0L, e.capacity == null ? null : (long) e.capacity, 0L, null, null);
    }
}
