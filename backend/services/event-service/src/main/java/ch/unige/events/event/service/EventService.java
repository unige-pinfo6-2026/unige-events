package ch.unige.events.event.service;

import ch.unige.events.shared.domain.projections.EventCapacity;
import ch.unige.events.shared.error.ApiErrorResponse;
import ch.unige.events.event.dto.CreateEventRequest;
import ch.unige.events.event.dto.EventDTO;
import ch.unige.events.event.dto.RecurrenceRequest;
import ch.unige.events.event.dto.UpdateEventRequest;
import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.favorite.entity.Favorite;
import ch.unige.events.event.util.RecurrenceGenerator;
import ch.unige.events.event.view.entity.EventView;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.projections.CallerIdentity;
import ch.unige.events.shared.kafka.events.EventLifecycleEvent;
import ch.unige.events.shared.storage.FileStorageService;
import io.quarkus.logging.Log;
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
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Same contract as the legacy ch.unige.events.service.EventService.
 *
 * <ul>
 *   <li>Décision F: {@code Event.creator} {@code @ManyToOne UserStub}
 *       replaced by {@code Event.creatorId UUID}.</li>
 *   <li>Décision I: bulk attendance counts come from
 *       {@link EngagementServiceClient#getAttendanceSummariesBulk(java.util.List)}
 *       instead of the deleted local {@code AttendanceStub}.</li>
 *   <li>EventViewStub / FavoriteStub were redundant copies of the local
 *       {@code EventView} / {@code Favorite} entities (same tables) —
 *       call sites use the local entities directly.</li>
 *   <li>{@code isCreator(event, auth0Id)} switches from
 *       UserStub.findByAuth0Id to CallerIdentity.</li>
 * </ul>
 */
@ApplicationScoped
public class EventService {

    @Inject FileStorageService fileStorageService;
    @Inject EntityManager entityManager;
    @Inject jakarta.enterprise.event.Event<EventLifecycleEvent> lifecycleEvent;
    @Inject CallerIdentity callerIdentity;

    @Inject @RestClient EngagementServiceClient engagementClient;
    @Inject @RestClient UserServiceClient userClient;

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
            conditions.add("e.creatorId = :organizerId");
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
        if (request.recurrence() != null) {
            return createRecurring(auth0Id, request);
        }
        Event event = persistParent(auth0Id, request);
        return EventDTO.from(event, 0L, EventCapacity.computeAvailableSpots(event.capacity, 0L), 0L, null, null);
    }

    @Transactional
    public EventDTO createRecurring(String auth0Id, CreateEventRequest request) {
        RecurrenceRequest recurrence = request.recurrence();
        if (recurrence.endDate() == null && recurrence.maxOccurrences() == null) {
            throw badRequestRecurrence("recurrence_unbounded",
                    "At least one of recurrence.endDate or recurrence.maxOccurrences must be provided.");
        }
        if (recurrence.endDate() != null
                && request.startDate() != null
                && recurrence.endDate().isBefore(request.startDate().toLocalDate())) {
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

        return EventDTO.from(parent, 0L, EventCapacity.computeAvailableSpots(parent.capacity, 0L), 0L, null, null);
    }

    @Transactional
    public List<EventDTO> getOccurrences(Long parentId, String auth0Id, boolean isAdmin, int page, int size) {
        getById(parentId, auth0Id, isAdmin);

        List<Event> occurrences = Event.<Event>find(
                "parentEventId = ?1 order by startDate asc, id asc",
                parentId
        ).page(page, size).list();

        UUID callerUuid = callerIdentity.getUuid();
        List<Event> visible = occurrences.stream()
                .filter(o -> isOccurrenceVisible(o, callerUuid, isAdmin))
                .toList();

        return toEventDTOs(visible);
    }

    private boolean isOccurrenceVisible(Event occurrence, UUID callerUuid, boolean isAdmin) {
        if (occurrence.status == EventStatus.BANNED) {
            return false;
        }
        if (occurrence.status == EventStatus.PUBLISHED) {
            return true;
        }
        return isAdmin || isCreatorOrAcceptedCoOrganizer(occurrence, callerUuid);
    }

    private Event persistParent(String auth0Id, CreateEventRequest request) {
        UUID creatorId = callerIdentity.requireUuid();
        if (creatorId == null) {
            throw new NotFoundException(
                    "User profile not found — call GET /users/me first");
        }

        Event event = new Event();
        event.title = request.title();
        event.description = request.description();
        event.location = request.location();
        event.startDate = request.startDate();
        event.endDate = request.endDate();
        event.category = request.category();
        event.faculty = request.faculty();
        event.bannerUrl = request.bannerUrl();
        event.capacity = request.capacity();
        event.allDay = Boolean.TRUE.equals(request.allDay());
        event.websiteUrl = request.websiteUrl();
        event.contactEmail = request.contactEmail();
        event.registrationDeadline = request.registrationDeadline();
        event.tags = normalizeTags(request.tags());
        event.creatorId = creatorId;
        if (request.status() == EventStatus.EXPIRED) {
            throw new BadRequestException("EXPIRED is a system-only status and cannot be set manually");
        }
        if (request.status() == EventStatus.CANCELLED) {
            throw new BadRequestException("CANCELLED is not a valid initial status");
        }
        if (request.status() == EventStatus.BANNED) {
            throw new BadRequestException("BANNED is a moderation-only status and cannot be set manually");
        }
        event.status = request.status() != null ? request.status() : EventStatus.DRAFT;
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
        occurrence.creatorId = parent.creatorId;
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
        return getById(id, auth0Id, isAdmin, null);
    }

    /**
     * Same as {@link #getById(Long, String, boolean)} but also fills the
     * {@code coOrganizerOf} field of the returned DTO when {@code checkCoOrgOf}
     * is non-null. Used by the {@code GET /events/{id}?check-co-org-of=}
     * cross-service endpoint so consumers (engagement, moderation) can
     * evaluate cascade SCRUM-136 in a single call.
     */
    public EventDTO getById(Long id, String auth0Id, boolean isAdmin, UUID checkCoOrgOf) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        if (event.status == EventStatus.BANNED) {
            throw new NotFoundException();
        }

        UUID callerUuid = callerIdentity.getUuid();
        if (event.status != EventStatus.PUBLISHED && !isAdmin
                && !isCreatorOrAcceptedCoOrganizer(event, callerUuid)) {
            throw new NotFoundException();
        }

        AttendanceSummary summary = engagementClient.getAttendanceSummary(id);
        long att = summary != null ? summary.attending() : 0L;
        long wait = summary != null ? summary.waitlisted() : 0L;
        Boolean coOrganizerOf = null;
        if (checkCoOrgOf != null) {
            coOrganizerOf = isCreatorOrAcceptedCoOrganizer(event, checkCoOrgOf);
        }
        return EventDTO.from(
                event,
                att,
                EventCapacity.computeAvailableSpots(event.capacity, att),
                wait,
                countViews(id),
                countInterested(id),
                coOrganizerOf
        );
    }

    /**
     * Cross-service bulk lookup. Returns the events whose ids are listed
     * (filtered by status when provided). Used by user-service's calendar
     * ICS feed to materialize a user's favorited / attended events into
     * an RFC 5545 stream.
     */
    public List<EventDTO> findByIds(List<Long> ids, EventStatus status) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Event> events = (status == null)
                ? Event.list("id IN ?1", ids)
                : Event.list("id IN ?1 AND status = ?2", ids, status);
        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> eventIds = events.stream().map(e -> e.id).toList();
        Map<Long, AttendanceSummary> summaries = engagementClient.getAttendanceSummariesBulk(eventIds);
        if (summaries == null) {
            summaries = Map.of();
        }
        List<EventDTO> result = new ArrayList<>(events.size());
        for (Event e : events) {
            AttendanceSummary s = summaries.getOrDefault(e.id, AttendanceSummary.of(0L, 0L));
            long a = s.attending();
            result.add(EventDTO.from(
                    e, a, EventCapacity.computeAvailableSpots(e.capacity, a),
                    s.waitlisted(), countViews(e.id), countInterested(e.id)
            ));
        }
        return result;
    }

    /**
     * Décision G of finalization-ultimate spec: returns the set of UUIDs
     * counting as "organizers" of an event = creator + ACCEPTED co-
     * organizers. Single REST call for engagement-service /
     * moderation-service consumers, replaces the legacy
     * {@code EventCoOrganizerStub.findAcceptedUserIdsForEvent}.
     *
     * <p>Anti-oracle: this endpoint is {@code @PermitAll} but applies a
     * minimal gate — 404 if the event is BANNED. We don't apply the
     * full ISSUE-92 cascade because the only consumer (engagement-
     * service) calls {@code /events/{id}?check-co-org-of=} first and
     * only invokes this endpoint after that visibility check passed.
     */
    public List<UUID> getOrganizerUuids(Long eventId) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);
        if (event.status == EventStatus.BANNED) {
            throw new NotFoundException();
        }
        Set<UUID> ids = new HashSet<>();
        if (event.creatorId != null) {
            ids.add(event.creatorId);
        }
        ids.addAll(EventCoOrganizer.findAcceptedUserIdsForEvent(eventId));
        return new ArrayList<>(ids);
    }

    @Transactional
    public EventDTO update(Long id, String auth0Id, UpdateEventRequest request) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        UUID callerUuid = callerIdentity.requireUuid();
        if (!isCreatorOrAcceptedCoOrganizer(event, callerUuid)) {
            throw new ForbiddenException("Only the event creator or an accepted co-organizer can update this event");
        }

        if (event.status == EventStatus.CANCELLED) {
            throw conflict("Cancelled events cannot be modified. Restore the event first.");
        }
        if (event.status == EventStatus.BANNED) {
            throw conflict("Banned events cannot be modified.");
        }

        event.title = request.title();
        event.description = request.description();
        event.location = request.location();
        event.startDate = request.startDate();
        event.endDate = request.endDate();
        event.category = request.category();
        event.faculty = request.faculty();
        event.bannerUrl = request.bannerUrl();
        event.capacity = request.capacity();
        event.allDay = Boolean.TRUE.equals(request.allDay());
        event.websiteUrl = request.websiteUrl();
        event.contactEmail = request.contactEmail();
        event.registrationDeadline = request.registrationDeadline();
        event.tags = normalizeTags(request.tags());
        if (request.status() != null) {
            if (request.status() == EventStatus.EXPIRED) {
                throw new BadRequestException("EXPIRED is a system-only status and cannot be set manually");
            }
            if (request.status() == EventStatus.BANNED) {
                throw new BadRequestException("BANNED is a moderation-only status and cannot be set manually");
            }
            event.status = request.status();
        }

        AttendanceSummary s = engagementClient.getAttendanceSummary(id);
        long att = s != null ? s.attending() : 0L;
        long wait = s != null ? s.waitlisted() : 0L;
        // SCRUM-99: CDI fire — bridge observer publishes events.updated to
        // Kafka AFTER_SUCCESS. Consumed by notification-service to fan out
        // EVENT_UPDATED notifications to attendees.
        lifecycleEvent.fire(EventLifecycleEvent.updated(event.id, event.creatorId));
        return EventDTO.from(event, att, EventCapacity.computeAvailableSpots(event.capacity, att), wait, null, null);
    }

    @Transactional
    public void delete(Long id, String auth0Id) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        UUID callerUuid = callerIdentity.requireUuid();
        if (!isCreator(event, callerUuid)) {
            throw new ForbiddenException("Only the event creator can delete this event");
        }

        if (event.status != EventStatus.CANCELLED) {
            throw conflict("Only cancelled events can be permanently deleted. Cancel the event first.");
        }

        // Local cleanup: favorites + event_views + event_co_organizers are
        // owned by event-service. attendances + comments live in
        // engagement-service — hard-delete cross-service is delegated to
        // engagement (orphan attendance rows for cancelled-then-deleted
        // events are handled by engagement-service's eventual GC ; deferred
        // S9 if needed). EVENT-DELETE-001: purge event_co_organizers so
        // PENDING/ACCEPTED rows do not survive a deleted parent (the table
        // has no ON DELETE CASCADE FK in V8).
        int favs = entityManager.createQuery("DELETE FROM Favorite f WHERE f.eventId = :id")
                .setParameter("id", id).executeUpdate();
        int views = entityManager.createQuery("DELETE FROM EventView v WHERE v.eventId = :id")
                .setParameter("id", id).executeUpdate();
        int coOrgs = entityManager.createQuery("DELETE FROM EventCoOrganizer co WHERE co.eventId = :id")
                .setParameter("id", id).executeUpdate();
        event.delete();
        Log.infof(
            "[EVENT_DELETE_CASCADE] event=%d caller=%s favorites=%d views=%d coOrgs=%d",
            id, auth0Id, favs, views, coOrgs
        );
    }

    @Transactional
    public EventDTO cancel(Long id, String auth0Id) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        UUID callerUuid = callerIdentity.requireUuid();
        if (!isCreatorOrAcceptedCoOrganizer(event, callerUuid)) {
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
        AttendanceSummary s = engagementClient.getAttendanceSummary(id);
        long attCancel = s != null ? s.attending() : 0L;
        long waitCancel = s != null ? s.waitlisted() : 0L;
        // CDI fire — bridge observer publishes to Kafka AFTER_SUCCESS
        // (Décision A — fixes BUG-002).
        lifecycleEvent.fire(EventLifecycleEvent.cancelled(event.id, event.creatorId));
        return EventDTO.from(event, attCancel, EventCapacity.computeAvailableSpots(event.capacity, attCancel), waitCancel, null, null);
    }

    @Transactional
    public EventDTO restore(Long id, String auth0Id) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        UUID callerUuid = callerIdentity.requireUuid();
        if (!isCreatorOrAcceptedCoOrganizer(event, callerUuid)) {
            throw new ForbiddenException("Only the event creator or an accepted co-organizer can restore this event");
        }

        if (event.status != EventStatus.CANCELLED) {
            throw conflict("Only cancelled events can be restored to draft");
        }

        event.status = EventStatus.DRAFT;
        AttendanceSummary s = engagementClient.getAttendanceSummary(id);
        long attRestore = s != null ? s.attending() : 0L;
        long waitRestore = s != null ? s.waitlisted() : 0L;
        return EventDTO.from(event, attRestore, EventCapacity.computeAvailableSpots(event.capacity, attRestore), waitRestore, null, null);
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

        UUID callerUuid = callerIdentity.requireUuid();
        if (!isAdmin && !isCreatorOrAcceptedCoOrganizer(event, callerUuid)) {
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
        AttendanceSummary s = engagementClient.getAttendanceSummary(id);
        long attPublish = s != null ? s.attending() : 0L;
        long waitPublish = s != null ? s.waitlisted() : 0L;
        // CDI fire — bridge observer publishes to Kafka AFTER_SUCCESS
        // (Décision A — fixes BUG-001 / BUG-002).
        lifecycleEvent.fire(EventLifecycleEvent.published(event.id, event.creatorId));
        return EventDTO.from(event, attPublish, EventCapacity.computeAvailableSpots(event.capacity, attPublish), waitPublish, null, null);
    }

    @Transactional
    public EventDTO uploadImage(Long id, String auth0Id, FileUpload fileUpload, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(id).orElseThrow(NotFoundException::new);

        UUID callerUuid = callerIdentity.requireUuid();
        if (!isAdmin && !isCreatorOrAcceptedCoOrganizer(event, callerUuid)) {
            throw new ForbiddenException("Only the event creator, an accepted co-organizer, or an admin can upload a banner");
        }

        event.bannerUrl = fileStorageService.saveImage(fileUpload, "events/banners",
                FileStorageService.MAX_BANNER_BYTES, event.bannerUrl);
        AttendanceSummary s = engagementClient.getAttendanceSummary(id);
        long attUpload = s != null ? s.attending() : 0L;
        long waitUpload = s != null ? s.waitlisted() : 0L;
        return EventDTO.from(event, attUpload, EventCapacity.computeAvailableSpots(event.capacity, attUpload), waitUpload, null, null);
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
        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> ids = events.stream().map(e -> e.id).toList();
        Map<Long, AttendanceSummary> summaries = engagementClient.getAttendanceSummariesBulk(ids);
        if (summaries == null) {
            summaries = Map.of();
        }
        Map<Long, AttendanceSummary> finalSummaries = summaries;
        return events.stream()
                .map(e -> {
                    AttendanceSummary s = finalSummaries.getOrDefault(
                            e.id, AttendanceSummary.of(0L, 0L));
                    long att = s.attending();
                    long wait = s.waitlisted();
                    return EventDTO.from(e, att, EventCapacity.computeAvailableSpots(e.capacity, att), wait, null, null);
                })
                .toList();
    }

    private static long countViews(Long eventId) {
        return EventView.count("eventId = ?1", eventId);
    }

    private static long countInterested(Long eventId) {
        return Favorite.count("eventId = ?1", eventId);
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

    private static boolean isCreator(Event event, UUID callerUuid) {
        return event.creatorId != null
                && callerUuid != null
                && event.creatorId.equals(callerUuid);
    }

    private boolean isCreatorOrAcceptedCoOrganizer(Event event, UUID callerUuid) {
        if (event == null || callerUuid == null) {
            return false;
        }
        if (callerUuid.equals(event.creatorId)) {
            return true;
        }
        return EventCoOrganizer.isAcceptedFor(event.id, callerUuid);
    }

    /**
     * Suppress UNUSED warning: kept for the Kafka consumer / scheduler
     * call sites that still pass auth0Id (legacy-compat). When the
     * caller is the system (auto-cleanup, expiration job) they pass
     * null and the JWT is also null, so the helper short-circuits.
     */
    @SuppressWarnings("unused")
    private boolean isCreatorOrAcceptedCoOrganizer(Event event, String auth0Id) {
        return isCreatorOrAcceptedCoOrganizer(event, callerIdentity.requireUuid());
    }
}
