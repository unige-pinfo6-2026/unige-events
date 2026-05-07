package ch.unige.events.service;

import ch.unige.events.dto.event.CreateEventRequest;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.dto.event.UpdateEventRequest;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventCoOrganizer;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.EventView;
import ch.unige.events.entity.Faculty;
import ch.unige.events.entity.Favorite;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class EventService {

    @Inject FileStorageService fileStorageService;

    @Inject EntityManager entityManager;

    @Transactional
    @SuppressWarnings("java:S107") // Filter-heavy list endpoint — flat params match the REST query signature 1:1.
    public List<EventDTO> getAll(int page, int size, EventStatus status, EventCategory category, UUID organizerId, LocalDateTime endDateFrom, Faculty faculty, Boolean facultyNone, Boolean featured) {
        StringBuilder jpql = new StringBuilder("SELECT e FROM Event e");
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        // facultyNone=true has priority over faculty — mutually exclusive filter.
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
            conditions.add("e.status <> :notExpired");
            params.put("notExpired", EventStatus.EXPIRED);
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
    public List<EventDTO> getMyEvents(String auth0Id, EventStatus status, int page, int size) {
        User user = User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        StringBuilder jpql = new StringBuilder("SELECT e FROM Event e WHERE e.creator.id = :creatorId");
        Map<String, Object> params = new HashMap<>();
        params.put("creatorId", user.id);
        if (status != null) {
            jpql.append(" AND e.status = :status");
            params.put("status", status);
        }
        jpql.append(" ORDER BY e.createdAt DESC, e.id DESC");

        List<Event> events = Event.<Event>find(jpql.toString(), params)
                .page(page, size)
                .list();

        return toEventDTOs(events);
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
        event.status = request.getStatus() != null ? request.getStatus() : EventStatus.DRAFT;
        event.persist();
        return EventDTO.from(event, 0L, computeAvailableSpots(event.capacity, 0L), 0L, null, null);
    }

    @Transactional
    public EventDTO getById(Long id, String auth0Id, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(NotFoundException::new);

        // Hotfix pentest 4.12 : hide DRAFT / CANCELLED events from non-owners / non-admins.
        // 404 (not 403) is intentional — same envelope as "does not exist" to close the
        // existence oracle highlighted in finding 4.12 (ID enumeration).
        // SCRUM-136 : un co-organisateur ACCEPTED peut aussi voir un DRAFT/CANCELLED.
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

        event.status = EventStatus.CANCELLED;
        long attCancel = countAttending(id);
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
        return EventDTO.from(event, attPublish, computeAvailableSpots(event.capacity, attPublish), countWaitlisted(id), null, null);
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

    private List<EventDTO> toEventDTOs(List<Event> events) {
        List<Long> ids = events.stream().map(e -> e.id).toList();
        Map<Long, Long> attendingCounts = Attendance.countGroupedByStatus(
                ids, AttendanceStatus.ATTENDING, entityManager);
        Map<Long, Long> waitlistedCounts = Attendance.countGroupedByStatus(
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
        return Attendance.count("eventId = ?1 and status = ?2", eventId, AttendanceStatus.ATTENDING);
    }

    private static long countWaitlisted(Long eventId) {
        return Attendance.count("eventId = ?1 and status = ?2", eventId, AttendanceStatus.WAITLISTED);
    }

    private static long countViews(Long eventId) {
        return EventView.count("eventId = ?1", eventId);
    }

    private static long countInterested(Long eventId) {
        return Favorite.count("eventId = ?1", eventId);
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

    /**
     * SCRUM-136 — Garde de cascade : créateur OU co-organisateur ACCEPTED.
     * Non-static car effectue une lookup DB (résolution auth0Id → User puis check
     * d'invitation ACCEPTED via {@link EventCoOrganizer#isAcceptedFor}).
     */
    private boolean isCreatorOrAcceptedCoOrganizer(Event event, String auth0Id) {
        if (isCreator(event, auth0Id)) {
            return true;
        }
        if (auth0Id == null) {
            return false;
        }
        return User.findByAuth0Id(auth0Id)
                .map(user -> EventCoOrganizer.isAcceptedFor(event.id, user.id))
                .orElse(false);
    }

    /**
     * SCRUM-136 — Exposition publique de la cascade pour les services voisins
     * ({@link AttendanceService}, {@link EventStatsService}). Le suffixe {@code Public}
     * dénote le wrapper visible hors-classe ; la logique vit dans la version privée.
     */
    public boolean isCreatorOrAcceptedCoOrganizerPublic(Event event, String auth0Id) {
        return isCreatorOrAcceptedCoOrganizer(event, auth0Id);
    }

    /**
     * SCRUM-136 — Bulk-resolve d'IDs d'événements vers leur projection EventDTO.
     * Réutilise {@link #toEventDTOs(List)} pour récupérer les compteurs
     * ATTENDING/WAITLISTED en une seule requête grouped-by. Utilisé par
     * {@link EventCoOrganizerService#getMyInvitations} pour enrichir
     * {@code CoOrganizerInvitationDTO} sans N+1.
     */
    @Transactional
    public Map<Long, EventDTO> findByIdsAsDTO(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Event> events = Event.<Event>list("id IN ?1", ids);
        List<EventDTO> dtos = toEventDTOs(events);
        Map<Long, EventDTO> result = new HashMap<>();
        for (EventDTO dto : dtos) {
            result.put(dto.id(), dto);
        }
        return result;
    }
}
