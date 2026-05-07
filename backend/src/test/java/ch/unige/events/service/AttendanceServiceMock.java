package ch.unige.events.service;

import ch.unige.events.MockEventFactory;
import ch.unige.events.dto.attendance.AttendanceDTO;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.Timeframe;
import ch.unige.events.entity.User;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Mock
@ApplicationScoped
public class AttendanceServiceMock extends AttendanceService {

    private final Map<Long, Event> eventsById = new ConcurrentHashMap<>();
    /** Map eventId → AttendanceStatus (une seule attendance par event pour simplifier les tests) */
    private final Map<Long, AttendanceStatus> attendances = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public static volatile boolean forceCapacityConflict = false;
    public static volatile boolean forceNotFoundOnAttend = false;
    public static volatile boolean forceNotFoundOnRemove = false;
    public static volatile boolean forceForbiddenOnGetAttendees = false;
    public static volatile boolean forceDraftConflict = false;
    public static volatile boolean forceRegistrationClosed = false;
    public static volatile boolean returnWaitlisted = false;

    public void reset() {
        eventsById.clear();
        attendances.clear();
        idSequence.set(1);
        forceCapacityConflict = false;
        forceNotFoundOnAttend = false;
        forceNotFoundOnRemove = false;
        forceForbiddenOnGetAttendees = false;
        forceDraftConflict = false;
        forceRegistrationClosed = false;
        returnWaitlisted = false;
    }

    public Event seedEvent(String title) {
        Event event = MockEventFactory.build(title, idSequence);
        eventsById.put(event.id, event);
        return event;
    }

    public void seedAttendance(Long eventId, AttendanceStatus status) {
        attendances.put(eventId, status);
    }

    @Override
    public AttendanceDTO attend(String auth0Id, Long eventId, AttendanceStatus status) {
        if (forceNotFoundOnAttend) throw new NotFoundException();
        if (forceDraftConflict) throw new BadRequestException("Cannot attend a non-published event");
        if (status != AttendanceStatus.ATTENDING) {
            throw new BadRequestException("Only ATTENDING is accepted as a request status");
        }
        if (forceRegistrationClosed) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(Map.of("error", "registration_closed", "message", "La deadline d'inscription est dépassée."))
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build());
        }
        if (forceCapacityConflict) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(Map.of("error", "conflict", "message", "Event has reached maximum capacity"))
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build());
        }
        if (!eventsById.containsKey(eventId)) throw new NotFoundException();
        AttendanceStatus effective = returnWaitlisted ? AttendanceStatus.WAITLISTED : status;
        attendances.put(eventId, effective);
        Attendance a = buildAttendance(eventId, effective);
        return AttendanceDTO.from(a, stubUser(a.userId));
    }

    @Override
    public void removeAttendance(String auth0Id, Long eventId) {
        if (forceNotFoundOnRemove) throw new NotFoundException();
        if (!attendances.containsKey(eventId)) throw new NotFoundException();
        attendances.remove(eventId);
    }

    @Override
    public List<AttendanceDTO> getAttendees(String auth0Id, Long eventId, int page, int size) {
        if (forceForbiddenOnGetAttendees) throw new ForbiddenException();
        if (!eventsById.containsKey(eventId)) throw new NotFoundException();
        return attendances.entrySet().stream()
                .filter(e -> e.getKey().equals(eventId))
                .map(e -> {
                    Attendance a = buildAttendance(e.getKey(), e.getValue());
                    return AttendanceDTO.from(a, stubUser(a.userId));
                })
                .skip((long) page * size)
                .limit(size)
                .toList();
    }

    @Override
    public List<AttendanceDTO> getMyAttendances(String auth0Id) {
        return attendances.entrySet().stream()
                .map(e -> {
                    Attendance a = buildAttendance(e.getKey(), e.getValue());
                    return AttendanceDTO.from(a, stubUser(a.userId));
                })
                .toList();
    }

    @Override
    public List<EventDTO> getMyParticipationEvents(String auth0Id, AttendanceStatus statusFilter, Timeframe timeframeFilter) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return attendances.entrySet().stream()
                .filter(e -> statusFilter == null || e.getValue() == statusFilter)
                .map(e -> eventsById.get(e.getKey()))
                .filter(Objects::nonNull)
                .filter(e -> matchesTimeframeMock(e, timeframeFilter, now))
                .map(e -> EventDTO.from(e, 0L, null, 0L, null, null))
                .toList();
    }

    private static boolean matchesTimeframeMock(Event event, Timeframe filter, java.time.LocalDateTime now) {
        if (filter == null) {
            return true;
        }
        if (event.endDate == null) {
            return false;
        }
        boolean isPast = event.endDate.isBefore(now);
        return filter == Timeframe.PAST ? isPast : !isPast;
    }

    private Attendance buildAttendance(Long eventId, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.id = idSequence.getAndIncrement();
        a.userId = UUID.randomUUID();
        a.eventId = eventId;
        a.status = status;
        a.createdAt = LocalDateTime.now();
        return a;
    }

    private User stubUser(UUID userId) {
        User u = new User();
        u.id = userId;
        u.displayName = "Mock User";
        u.avatarUrl = null;
        return u;
    }
}
