package ch.unige.events.event.service;

import ch.unige.events.event.dto.ApiErrorResponse;
import ch.unige.events.shared.kafka.events.EventLifecycleEvent;
import ch.unige.events.shared.storage.FileStorageService;
import ch.unige.events.event.dto.CreateEventRequest;
import ch.unige.events.event.dto.EventDTO;
import ch.unige.events.event.dto.RecurrenceRequest;
import ch.unige.events.event.dto.UpdateEventRequest;
import ch.unige.events.event.entity.AttendanceStatus;
import ch.unige.events.event.entity.AttendanceStub;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.entity.EventCategory;
import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.entity.EventStatus;
import ch.unige.events.event.entity.EventViewStub;
import ch.unige.events.event.entity.Faculty;
import ch.unige.events.event.entity.FavoriteStub;
import ch.unige.events.event.entity.UserStub;
import ch.unige.events.event.util.RecurrenceGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Same contract as the legacy ch.unige.events.service.EventService.
 *
 * <p>Notable adaptations for the soft-extraction :
 * <ul>
 *   <li>Image upload delegates to the shared {@link FileStorageService}
 *       (deduped between user-service and event-service in the
 *       shared-storage lib).</li>
 *   <li>Cross-domain entities replaced by stubs : {@link AttendanceStub},
 *       {@link EventViewStub}, {@link FavoriteStub},
 *       {@link EventCoOrganizer}, {@link UserStub}. The legacy JPQL
 *       queries are retyped to the stub names.</li>
 * </ul>
 */
@ApplicationScoped
public class EventService {

    @Inject FileStorageService fileStorageService;
    @Inject EntityManager entityManager;
    @Inject jakarta.enterprise.event.Event<EventLifecycleEvent> lifecycleEvent;

    @Transactional
    @SuppressWarnings("java:S107")
    public List<EventDTO> getAll(int page, int size, EventStatus status, EventCategory category, UUID organizerId, LocalDateTime endDateFrom, Faculty faculty, Boolean facultyNone, Boolean featured) {
        StringBuilder jpql = new StringBuilder("SELECT e FROM Event e");
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        if (Boolean.TRUE.equals(facultyNone)) {
            conditions.add("e.faculty IS NULL");
        } else if (faculty != null) {
            conditions.add("e.faculty = :faculty");
            params.put("faculty", faculty);
        }
        if (status != null) {
            conditions.add("e.status = :status");
            params.put("status", status);
        } else {
            conditions.add("e.status NOT IN (:hiddenStatuses)");
            params.put("hiddenStatuses", List.of(EventStatus.EXPIRED, EventStatus.BANNED));
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
        if (Boolean.TRUE.equals(featured)) {
            conditions.add("e.featured = true");
        }

        if (!conditions.isEmpty()) {
            jpql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        jpql.append(" ORDER BY e.startDate, e.id");

        List<Event> events = Event.<Event>find(jpql.toString(), params)
                .page(page, size)
                .list();

        return toEventDTOs(events);
    }

    @Transactional
    public EventDTO create(String auth0Id, CreateEventRequest request) {
        if (request.recurrence != null) {
            return createRecurring(auth0Id, request);
        }
        Event event = persistParent(auth0Id, request);
        return EventDTO.from(event, 0L, computeAvailableSpots(event.capacity, 0L), 0L, null, null);
    }

    @Transactional
    public EventDTO createRecurring(String auth0Id, CreateEventRequest request) {
        RecurrenceRequest recurrence = request.recurrence;
        if (recurrence.endDate() == null && recurrence.maxOccurrences() == null) {
            throw badRequestRecurrence("recurrence_unbounded",
                    "At least one of recurrence.endDate or recurrence.maxOccurrences must be provided.");
        }
        if (recurrence.endDate() != null
                && request.startDate != null
                && recurrence.endDate().isBefore(request.startDate.toLocalDate())) {
            throw badRequestRecurrence("recurrence_end_before_start",
                    "recurrence.endDate must be greater than or equal to startDate.");
        }

        Event parent = persistParent(auth0Id, request);
        parent.recurrenceRule = buildRecurrenceRule(recurrence);

        List<RecurrenceGenerator.DateRange> ranges = RecurrenceGenerator.generate(
                parent.startDate,
                parent.endDate,
                recurrence.frequency(),
                recurrence.endDate(),
                recurrence.maxOccurrences());

        for (RecurrenceGenerator.DateRange range : ranges) {
            persistOccurrence(parent, range);
        }

        return EventDTO.from(parent, 0L, computeAvailableSpots(parent.capacity, 0L), 0L, null, null);
    }

    @Transactional
    public List<EventDTO> getOccurrences(Long parentId, String auth0Id, boolean isAdmin, int page, int size) {
        getById(parentId, auth0Id, isAdmin);

        List<Event> occurrences = Event.<Event>find(
                "parentEventId = ?1 order by startDate asc, id asc",
                parentId
        ).page(page, size).list();

        List<Event> visible = occurrences.stream()
                .filter(o -> isOccurrenceVisible(o, auth0Id, isAdmin))
                .toList();

        return toEventDTOs(visible);
    }

    private boolean isOccurrenceVisible(Event occurrence, String auth0Id, boolean isAdmin) {
        if (occurrence.status == EventStatus.BANNED) {
            return false;
        }
        if (occurrence.status == EventStatus.PUBLISHED) {
            return true;
        }
        return isAdmin || isCreatorOrAcceptedCoOrganizer(occurrence, auth0Id);
    }

    private Event persistParent(String auth0Id, CreateEventRequest request) {
        UserStub creator = UserStub.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        Event event = new Event();
        event.title = request.title;
        event.description = request.description;
        event.location = request.location;
        event.startDate = request.startDate;
        event.endDate = request.endDate;
        event.category = request.category;
        event.faculty = request.faculty;
        event.bannerUrl = request.bannerUrl;
        event.capacity = request.capacity;
        event.allDay = Boolean.TRUE.equals(request.allDay);
        event.websiteUrl = request.websiteUrl;
        event.contactEmail = request.contactEmail;
        event.registrationDeadline = request.registrationDeadline;
        event.tags = normalizeTags(request.tags);
        event.creator = creator;
        if (request.getStatus() == EventStatus.EXPIRED) {
            throw new BadRequestException("EXPIRED is a system-only status and cannot be set manually");
        }
        if (request.getStatus() == EventStatus.CANCELLED) {
            throw new BadRequestException("CANCELLED is not a valid initial status");
        }
        if (request.getStatus() == EventStatus.BANNED) {
            throw new BadRequestException("BANNED is a moderation-only status and cannot be set manually");
        }
        event.status = request.getStatus() != null ? request.getStatus() : EventStatus.DRAFT;
        event.persist();
        return event;
    }

    private void persistOccurrence(Event parent, RecurrenceGenerator.DateRange range) {
        Event occurrence = new Event();
        occurrence.title = parent.title;
        occurrence.description = parent.description;
        occurrence.location = parent.location;
        occurrence.startDate = range.start();
        occurrence.endDate = range.end();
        occurrence.category = parent.category;
        occurrence.faculty = parent.faculty;
        occurrence.bannerUrl = parent.bannerUrl;
        occurrence.capacity = parent.capacity;
        occurrence.allDay = parent.allDay;
        occurrence.websiteUrl = parent.websiteUrl;
        occurrence.contactEmail = parent.contactEmail;
        occurrence.registrationDeadline = parent.registrationDeadline;
        occurrence.tags = parent.tags == null ? new ArrayList<>() : new ArrayList<>(parent.tags);
        occurrence.creator = parent.creator;
        occurrence.status = parent.status;
        occurrence.parentEventId = parent.id;
        occurrence.recurrenceRule = null;
        occurrence.persist();
    }

    static String buildRecurrenceRule(RecurrenceRequest r) {
        StringBuilder sb = new StringBuilder("FREQ=").append(r.frequency().name());
        if (r.endDate() != null) {
            sb.append(";UNTIL=").append(r.endDate().format(DateTimeFormatter.BASIC_ISO_DATE));
        }
        if (r.maxOccurrences() != null) {
            sb.append(";COUNT=").append(r.maxOccurrences());
        }
        return sb.toString();
    }

    private static WebApplicationException badRequestRecurrence(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    @Transactional
    public EventDTO getById(Long id, String auth0Id, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        if (event.status == EventStatus.BANNED) {
            throw new NotFoundException();
        }

        if (event.status != EventStatus.PUBLISHED && !isAdmin && !isCreatorOrAcceptedCoOrganizer(event, auth0Id)) {
            throw new NotFoundException();
        }

        long att = countAttending(id);
        return EventDTO.from(
                event,
                att,
                computeAvailableSpots(event.capacity, att),
                countWaitlisted(id),
                countViews(id),
                countInterested(id)
        );
    }

    @Transactional
    public EventDTO update(Long id, String auth0Id, UpdateEventRequest request) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        if (!isCreatorOrAcceptedCoOrganizer(event, auth0Id)) {
            throw new ForbiddenException("Only the event creator or an accepted co-organizer can update this event");
        }

        if (event.status == EventStatus.CANCELLED) {
            throw conflict("Cancelled events cannot be modified. Restore the event first.");
        }
        if (event.status == EventStatus.BANNED) {
            throw conflict("Banned events cannot be modified.");
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
        event.allDay = Boolean.TRUE.equals(request.allDay);
        event.websiteUrl = request.websiteUrl;
        event.contactEmail = request.contactEmail;
        event.registrationDeadline = request.registrationDeadline;
        event.tags = normalizeTags(request.tags);
        if (request.status != null) {
            if (request.status == EventStatus.EXPIRED) {
                throw new BadRequestException("EXPIRED is a system-only status and cannot be set manually");
            }
            if (request.status == EventStatus.BANNED) {
                throw new BadRequestException("BANNED is a moderation-only status and cannot be set manually");
            }
            event.status = request.status;
        }

        long att = countAttending(id);
        return EventDTO.from(event, att, computeAvailableSpots(event.capacity, att), countWaitlisted(id), null, null);
    }

    @Transactional
    public void delete(Long id, String auth0Id) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        if (!isCreator(event, auth0Id)) {
            throw new ForbiddenException("Only the event creator can delete this event");
        }

        if (event.status != EventStatus.CANCELLED) {
            throw conflict("Only cancelled events can be permanently deleted. Cancel the event first.");
        }

        entityManager.createQuery("DELETE FROM AttendanceStub a WHERE a.eventId = :id").setParameter("id", id).executeUpdate();
        entityManager.createQuery("DELETE FROM FavoriteStub f WHERE f.eventId = :id").setParameter("id", id).executeUpdate();
        entityManager.createQuery("DELETE FROM EventViewStub v WHERE v.eventId = :id").setParameter("id", id).executeUpdate();
        event.delete();
    }

    @Transactional
    public EventDTO cancel(Long id, String auth0Id) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        if (!isCreatorOrAcceptedCoOrganizer(event, auth0Id)) {
            throw new ForbiddenException("Only the event creator or an accepted co-organizer can cancel this event");
        }

        if (event.status == EventStatus.CANCELLED) {
            throw conflict("Event is already cancelled");
        }
        if (event.status == EventStatus.BANNED) {
            throw conflict("Banned events cannot be cancelled by their creator.");
        }
        if (event.status == EventStatus.EXPIRED) {
            throw conflict("Expired events cannot be cancelled.");
        }

        event.status = EventStatus.CANCELLED;
        long attCancel = countAttending(id);
        // CDI fire — the EventLifecycleKafkaBridge observer routes to
        // the Kafka publisher only AFTER_SUCCESS commit (Décision A —
        // fixes BUG-002). A rollback aborts the delivery.
        lifecycleEvent.fire(EventLifecycleEvent.cancelled(event.id, event.creator != null ? event.creator.id : null));
        return EventDTO.from(event, attCancel, computeAvailableSpots(event.capacity, attCancel), countWaitlisted(id), null, null);
    }

    @Transactional
    public EventDTO restore(Long id, String auth0Id) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        if (!isCreatorOrAcceptedCoOrganizer(event, auth0Id)) {
            throw new ForbiddenException("Only the event creator or an accepted co-organizer can restore this event");
        }

        if (event.status != EventStatus.CANCELLED) {
            throw conflict("Only cancelled events can be restored to draft");
        }

        event.status = EventStatus.DRAFT;
        long attRestore = countAttending(id);
        return EventDTO.from(event, attRestore, computeAvailableSpots(event.capacity, attRestore), countWaitlisted(id), null, null);
    }

    private static WebApplicationException conflict(String message) {
        return new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("error", "conflict", "message", message))
                        .build());
    }

    @Transactional
    public EventDTO publish(Long id, String auth0Id, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(id).orElseThrow(NotFoundException::new);

        if (!isAdmin && !isCreatorOrAcceptedCoOrganizer(event, auth0Id)) {
            throw new ForbiddenException("Only the event creator, an accepted co-organizer, or an admin can publish this event");
        }

        if (event.status != EventStatus.DRAFT) {
            String message = event.status == EventStatus.PUBLISHED
                    ? "Event is already published"
                    : "Event cannot be published: current status is " + event.status;
            throw conflict(message);
        }

        List<String> errors = collectPublishValidationErrors(event);
        if (!errors.isEmpty()) {
            throw new WebApplicationException(
                    Response.status(422)
                            .entity(Map.of("error", "validation_failed", "errors", errors))
                            .build());
        }

        event.status = EventStatus.PUBLISHED;
        long attPublish = countAttending(id);
        // CDI fire — bridge observer publishes to Kafka AFTER_SUCCESS
        // (Décision A — fixes BUG-001 / BUG-002).
        lifecycleEvent.fire(EventLifecycleEvent.published(event.id, event.creator != null ? event.creator.id : null));
        return EventDTO.from(event, attPublish, computeAvailableSpots(event.capacity, attPublish), countWaitlisted(id), null, null);
    }

    @Transactional
    public EventDTO uploadImage(Long id, String auth0Id, FileUpload fileUpload, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(id).orElseThrow(NotFoundException::new);

        if (!isAdmin && !isCreatorOrAcceptedCoOrganizer(event, auth0Id)) {
            throw new ForbiddenException("Only the event creator, an accepted co-organizer, or an admin can upload a banner");
        }

        event.bannerUrl = fileStorageService.saveImage(fileUpload, "events/banners",
                FileStorageService.MAX_BANNER_BYTES, event.bannerUrl);
        long attUpload = countAttending(id);
        return EventDTO.from(event, attUpload, computeAvailableSpots(event.capacity, attUpload), countWaitlisted(id), null, null);
    }

    private static List<String> collectPublishValidationErrors(Event event) {
        List<String> errors = new ArrayList<>();
        if (event.title == null || event.title.isBlank()) {
            errors.add("Le titre est obligatoire");
        }
        if (event.location == null || event.location.isBlank()) {
            errors.add("Le lieu est obligatoire");
        }
        if (event.category == null) {
            errors.add("La catégorie est obligatoire");
        }
        if (event.startDate == null || !event.startDate.isAfter(LocalDateTime.now())) {
            errors.add("La date de l'événement doit être dans le futur");
        }
        if (event.endDate == null) {
            errors.add("La date de fin est obligatoire");
        } else if (event.startDate != null && !event.endDate.isAfter(event.startDate)) {
            errors.add("La date de fin doit être après la date de début");
        }
        return errors;
    }

    private List<EventDTO> toEventDTOs(List<Event> events) {
        List<Long> ids = events.stream().map(e -> e.id).toList();
        Map<Long, Long> attendingCounts = AttendanceStub.countGroupedByStatus(
                ids, AttendanceStatus.ATTENDING, entityManager);
        Map<Long, Long> waitlistedCounts = AttendanceStub.countGroupedByStatus(
                ids, AttendanceStatus.WAITLISTED, entityManager);
        return events.stream()
                .map(e -> {
                    long att = attendingCounts.getOrDefault(e.id, 0L);
                    long wait = waitlistedCounts.getOrDefault(e.id, 0L);
                    return EventDTO.from(e, att, computeAvailableSpots(e.capacity, att), wait, null, null);
                })
                .toList();
    }

    private static long countAttending(Long eventId) {
        return AttendanceStub.count("eventId = ?1 and status = ?2", eventId, AttendanceStatus.ATTENDING);
    }

    private static long countWaitlisted(Long eventId) {
        return AttendanceStub.count("eventId = ?1 and status = ?2", eventId, AttendanceStatus.WAITLISTED);
    }

    private static long countViews(Long eventId) {
        return EventViewStub.count("eventId = ?1", eventId);
    }

    private static long countInterested(Long eventId) {
        return FavoriteStub.count("eventId = ?1", eventId);
    }

    static Long computeAvailableSpots(Integer capacity, long attendingCount) {
        if (capacity == null) {
            return null;
        }
        return Math.max(0L, capacity.longValue() - attendingCount);
    }

    static List<String> normalizeTags(List<String> input) {
        if (input == null || input.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String raw : input) {
            if (raw == null) continue;
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                seen.add(normalized);
            }
        }
        return new ArrayList<>(seen);
    }

    private static boolean isCreator(Event event, String auth0Id) {
        return event.creator != null
                && event.creator.auth0Id != null
                && event.creator.auth0Id.equals(auth0Id);
    }

    private boolean isCreatorOrAcceptedCoOrganizer(Event event, String auth0Id) {
        if (isCreator(event, auth0Id)) {
            return true;
        }
        if (auth0Id == null) {
            return false;
        }
        return UserStub.findByAuth0Id(auth0Id)
                .map(user -> EventCoOrganizer.isAcceptedFor(event.id, user.id))
                .orElse(false);
    }
}
