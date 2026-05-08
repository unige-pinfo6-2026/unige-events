package ch.unige.events.service;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.dto.attendance.AttendanceDTO;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Timeframe;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class AttendanceService {

    private static final Logger LOG = Logger.getLogger(AttendanceService.class);

    @Inject
    EntityManager entityManager;

    @Inject
    EventService eventService;

    @Transactional
    public AttendanceDTO attend(String auth0Id, Long eventId, AttendanceStatus status) {
        if (status != AttendanceStatus.ATTENDING) {
            throw new BadRequestException("Only ATTENDING is accepted as a request status");
        }

        // Verrou pessimiste pris tôt et systématiquement : sérialise les attends/removes
        // concurrents sur le même event et supprime la race sur l'unique constraint
        // (userId, eventId). La pré-existence, le count capacité et l'insertion vivent
        // tous sous ce verrou.
        Event event = entityManager.find(Event.class, eventId, LockModeType.PESSIMISTIC_WRITE);
        if (event == null) {
            throw new NotFoundException("Event not found");
        }

        if (event.status != EventStatus.PUBLISHED) {
            throw new BadRequestException("Cannot attend a non-published event");
        }

        // Vérification deadline d'inscription (SCRUM-126)
        if (event.registrationDeadline != null
                && LocalDateTime.now().isAfter(event.registrationDeadline)) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(new ApiErrorResponse(
                                    "registration_closed",
                                    "La deadline d'inscription est dépassée."))
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build());
        }

        User user = resolveUser(auth0Id);

        // Idempotence sous verrou : si déjà inscrit (ATTENDING ou WAITLISTED), renvoyer tel quel
        Attendance existing = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", user.id, eventId)
                .firstResultOptional()
                .orElse(null);
        if (existing != null) {
            return AttendanceDTO.from(existing, user);
        }

        // Détermination du statut effectif (ATTENDING ou WAITLISTED)
        AttendanceStatus effective;
        if (event.capacity == null) {
            effective = AttendanceStatus.ATTENDING;
        } else {
            long currentAttending = Attendance.count(
                    "eventId = ?1 and status = ?2", eventId, AttendanceStatus.ATTENDING);
            effective = (currentAttending < event.capacity)
                    ? AttendanceStatus.ATTENDING
                    : AttendanceStatus.WAITLISTED;
        }

        Attendance attendance = new Attendance();
        attendance.userId = user.id;
        attendance.eventId = eventId;
        attendance.status = effective;
        attendance.persist();

        return AttendanceDTO.from(attendance, user);
    }

    @Transactional
    public void removeAttendance(String auth0Id, Long eventId) {
        UUID userId = resolveUserId(auth0Id);

        Attendance attendance = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", userId, eventId)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Attendance not found"));

        AttendanceStatus removed = attendance.status;

        // Verrou pessimiste pris AVANT le delete : sérialise avec les attends concurrents
        // et garantit que la décision de promotion voit un état cohérent du compteur.
        Event event = entityManager.find(Event.class, eventId, LockModeType.PESSIMISTIC_WRITE);

        attendance.delete();

        // Promotion uniquement si on libère un slot ATTENDING, sur un event avec capacité,
        // qui n'est pas annulé.
        if (removed != AttendanceStatus.ATTENDING
                || event == null
                || event.capacity == null
                || event.status == EventStatus.CANCELLED
                || event.status == EventStatus.EXPIRED
                || event.status == EventStatus.BANNED) {
            return;
        }

        Attendance promoted = Attendance.<Attendance>find(
                "eventId = ?1 and status = ?2 order by createdAt asc, id asc",
                eventId, AttendanceStatus.WAITLISTED)
                .firstResultOptional()
                .orElse(null);
        if (promoted == null) {
            return;
        }

        promoted.status = AttendanceStatus.ATTENDING;
        LOG.infof("[WAITLIST_PROMOTION] event=%d user=%s promoted from WAITLISTED to ATTENDING",
                eventId, promoted.userId);
    }

    @Transactional
    public List<AttendanceDTO> getAttendees(String auth0Id, Long eventId, int page, int size) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        // SCRUM-136 : créateur OU co-organisateur ACCEPTED.
        if (!eventService.isCreatorOrAcceptedCoOrganizerPublic(event, auth0Id)) {
            throw new ForbiddenException("Only the event creator or an accepted co-organizer can view attendees");
        }

        List<Attendance> rows = Attendance.findByEvent(eventId, page, size);
        Map<UUID, User> usersById = loadUsersByIds(
                rows.stream().map(a -> a.userId).collect(Collectors.toSet()));

        return rows.stream()
                .map(a -> AttendanceDTO.from(a, usersById.get(a.userId)))
                .toList();
    }

    @Transactional
    public List<AttendanceDTO> getMyAttendances(String auth0Id) {
        User user = resolveUser(auth0Id);
        return Attendance.findAllByUser(user.id).stream()
                .map(a -> AttendanceDTO.from(a, user))
                .toList();
    }

    /**
     * Returns the events the current user is registered to, optionally filtered by
     * attendance status (ATTENDING / WAITLISTED) and timeframe (UPCOMING / PAST).
     * Each event is enriched with the same counts/availableSpots projection used
     * by other "/users/me/..." event lists so the frontend can render EventCards
     * without N+1 fetches.
     *
     * <p>Timeframe is computed against the event's {@code endDate} relative to
     * {@link LocalDateTime#now()}. {@link Timeframe#UPCOMING} matches events whose
     * end date is &gt;= now (an event in progress is upcoming), {@link Timeframe#PAST}
     * matches events whose end date is &lt; now.
     *
     * @param auth0Id         the Auth0 subject of the current user
     * @param statusFilter    optional status filter; null = all statuses
     * @param timeframeFilter optional timeframe filter; null = all timeframes
     */
    @Transactional
    public List<EventDTO> getMyParticipationEvents(String auth0Id, AttendanceStatus statusFilter, Timeframe timeframeFilter) {
        User user = resolveUser(auth0Id);
        List<Attendance> rows = Attendance.findAllByUser(user.id);
        List<Long> eventIds = rows.stream()
                .filter(a -> statusFilter == null || a.status == statusFilter)
                .map(a -> a.eventId)
                .toList();
        if (eventIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> attendingCounts = Attendance.countGroupedByStatus(eventIds, AttendanceStatus.ATTENDING, entityManager);
        Map<Long, Long> waitlistedCounts = Attendance.countGroupedByStatus(eventIds, AttendanceStatus.WAITLISTED, entityManager);
        // Bulk-load every Event in one query (avoids the N+1 that a per-id
        // findByIdOptional() loop would produce). Preserves the order of the
        // attendance rows by re-indexing client-side.
        Map<Long, Event> eventsById = Event.<Event>list("id in ?1", eventIds).stream()
                .collect(Collectors.toMap(e -> e.id, e -> e));
        LocalDateTime now = LocalDateTime.now();
        return eventIds.stream()
                .map(eventsById::get)
                .filter(java.util.Objects::nonNull)
                .filter(e -> matchesTimeframe(e, timeframeFilter, now))
                .map(e -> {
                    long att = attendingCounts.getOrDefault(e.id, 0L);
                    long wait = waitlistedCounts.getOrDefault(e.id, 0L);
                    return EventDTO.from(e, att, EventService.computeAvailableSpots(e.capacity, att), wait, null, null);
                })
                .toList();
    }

    private static boolean matchesTimeframe(Event event, Timeframe filter, LocalDateTime now) {
        if (filter == null) {
            return true;
        }
        if (event.endDate == null) {
            // Defensive: events without an endDate cannot be classified — exclude them
            // from any explicit timeframe filter so we never lie to the caller.
            return false;
        }
        boolean isPast = event.endDate.isBefore(now);
        return filter == Timeframe.PAST ? isPast : !isPast;
    }

    private User resolveUser(String auth0Id) {
        return User.<User>find("auth0Id", auth0Id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User profile not found"));
    }

    private UUID resolveUserId(String auth0Id) {
        return resolveUser(auth0Id).id;
    }

    /**
     * Single SELECT loading every {@link User} referenced by a batch of attendance rows.
     * Used by {@link #getAttendees(String, Long, int, int)} to avoid the N+1 that would
     * otherwise happen if each row triggered its own User lookup.
     */
    private Map<UUID, User> loadUsersByIds(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return new HashMap<>();
        }
        List<User> users = User.<User>list("id in ?1", userIds);
        Map<UUID, User> byId = new HashMap<>();
        for (User u : users) {
            byId.put(u.id, u);
        }
        return byId;
    }
}
