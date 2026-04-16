package ch.unige.events.service;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.dto.attendance.AttendanceDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
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
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AttendanceService {

    private static final Logger LOG = Logger.getLogger(AttendanceService.class);

    @Inject
    EntityManager entityManager;

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

        UUID userId = resolveUserId(auth0Id);

        // Idempotence sous verrou : si déjà inscrit (ATTENDING ou WAITLISTED), renvoyer tel quel
        Attendance existing = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", userId, eventId)
                .firstResultOptional()
                .orElse(null);
        if (existing != null) {
            return AttendanceDTO.from(existing);
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
        attendance.userId = userId;
        attendance.eventId = eventId;
        attendance.status = effective;
        attendance.persist();

        return AttendanceDTO.from(attendance);
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
                || event.status == EventStatus.CANCELLED) {
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

        if (event.creator == null || event.creator.auth0Id == null || !event.creator.auth0Id.equals(auth0Id)) {
            throw new ForbiddenException("Only the event creator can view attendees");
        }

        return Attendance.findByEvent(eventId, page, size).stream()
                .map(AttendanceDTO::from)
                .toList();
    }

    @Transactional
    public List<AttendanceDTO> getMyAttendances(String auth0Id) {
        UUID userId = resolveUserId(auth0Id);
        return Attendance.findAllByUser(userId).stream()
                .map(AttendanceDTO::from)
                .toList();
    }

    private UUID resolveUserId(String auth0Id) {
        return User.<User>find("auth0Id", auth0Id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User profile not found"))
                .id;
    }
}
