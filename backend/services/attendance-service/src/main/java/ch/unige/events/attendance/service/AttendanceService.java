package ch.unige.events.attendance.service;

import ch.unige.events.attendance.dto.ApiErrorResponse;
import ch.unige.events.attendance.dto.AttendanceDTO;
import ch.unige.events.attendance.dto.EventDTO;
import ch.unige.events.attendance.entity.Attendance;
import ch.unige.events.attendance.entity.AttendanceStatus;
import ch.unige.events.attendance.entity.EventCoOrganizerStub;
import ch.unige.events.attendance.entity.EventStatus;
import ch.unige.events.attendance.entity.EventStub;
import ch.unige.events.attendance.entity.Timeframe;
import ch.unige.events.attendance.entity.UserStub;

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

/**
 * Same contract as the legacy AttendanceService — pessimistic-lock-based
 * capacity gating, idempotent attend, WAITLISTED auto-promotion on
 * remove. The SCRUM-136 cascade is inlined locally (will become a REST
 * call to co-organizer-service in a follow-up cleanup).
 */
@ApplicationScoped
public class AttendanceService {

    private static final Logger LOG = Logger.getLogger(AttendanceService.class);

    @Inject EntityManager entityManager;

    @Transactional
    public AttendanceDTO attend(String auth0Id, Long eventId, AttendanceStatus status) {
        if (status != AttendanceStatus.ATTENDING) {
            throw new BadRequestException("Only ATTENDING is accepted as a request status");
        }

        EventStub event = entityManager.find(EventStub.class, eventId, LockModeType.PESSIMISTIC_WRITE);
        if (event == null) {
            throw new NotFoundException("Event not found");
        }

        if (event.status != EventStatus.PUBLISHED) {
            throw new BadRequestException("Cannot attend a non-published event");
        }

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

        UserStub user = resolveUser(auth0Id);

        Attendance existing = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", user.id, eventId)
                .firstResultOptional()
                .orElse(null);
        if (existing != null) {
            return AttendanceDTO.from(existing, user);
        }

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

        EventStub event = entityManager.find(EventStub.class, eventId, LockModeType.PESSIMISTIC_WRITE);

        attendance.delete();

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
        EventStub event = EventStub.<EventStub>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UserStub caller = UserStub.findByAuth0Id(auth0Id).orElse(null);
        if (!isCreatorOrAcceptedCoOrganizer(event, caller)) {
            throw new ForbiddenException("Only the event creator or an accepted co-organizer can view attendees");
        }

        List<Attendance> rows = Attendance.findByEvent(eventId, page, size);
        Map<UUID, UserStub> usersById = loadUsersByIds(
                rows.stream().map(a -> a.userId).collect(Collectors.toSet()));

        return rows.stream()
                .map(a -> AttendanceDTO.from(a, usersById.get(a.userId)))
                .toList();
    }

    @Transactional
    public List<AttendanceDTO> getMyAttendances(String auth0Id) {
        UserStub user = resolveUser(auth0Id);
        return Attendance.findAllByUser(user.id).stream()
                .map(a -> AttendanceDTO.from(a, user))
                .toList();
    }

    @Transactional
    public List<EventDTO> getMyParticipationEvents(String auth0Id, AttendanceStatus statusFilter, Timeframe timeframeFilter) {
        UserStub user = resolveUser(auth0Id);
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
        Map<Long, EventStub> eventsById = EventStub.<EventStub>list("id in ?1", eventIds).stream()
                .collect(Collectors.toMap(e -> e.id, e -> e));
        LocalDateTime now = LocalDateTime.now();
        return eventIds.stream()
                .map(eventsById::get)
                .filter(java.util.Objects::nonNull)
                .filter(e -> matchesTimeframe(e, timeframeFilter, now))
                .map(e -> {
                    long att = attendingCounts.getOrDefault(e.id, 0L);
                    long wait = waitlistedCounts.getOrDefault(e.id, 0L);
                    return EventDTO.from(e, att, computeAvailableSpots(e.capacity, att), wait, null, null);
                })
                .toList();
    }

    private static boolean matchesTimeframe(EventStub event, Timeframe filter, LocalDateTime now) {
        if (filter == null) {
            return true;
        }
        if (event.endDate == null) {
            return false;
        }
        boolean isPast = event.endDate.isBefore(now);
        return filter == Timeframe.PAST ? isPast : !isPast;
    }

    private static Long computeAvailableSpots(Integer capacity, long attendingCount) {
        if (capacity == null) {
            return null;
        }
        return Math.max(0L, capacity.longValue() - attendingCount);
    }

    private static boolean isCreatorOrAcceptedCoOrganizer(EventStub event, UserStub caller) {
        if (event == null || caller == null) {
            return false;
        }
        if (caller.id.equals(event.creatorId)) {
            return true;
        }
        return EventCoOrganizerStub.isAcceptedFor(event.id, caller.id);
    }

    private UserStub resolveUser(String auth0Id) {
        return UserStub.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"));
    }

    private UUID resolveUserId(String auth0Id) {
        return resolveUser(auth0Id).id;
    }

    private Map<UUID, UserStub> loadUsersByIds(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return new HashMap<>();
        }
        List<UserStub> users = UserStub.<UserStub>list("id in ?1", userIds);
        Map<UUID, UserStub> byId = new HashMap<>();
        for (UserStub u : users) {
            byId.put(u.id, u);
        }
        return byId;
    }
}
