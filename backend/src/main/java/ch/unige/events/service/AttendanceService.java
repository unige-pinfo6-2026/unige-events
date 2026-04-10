package ch.unige.events.service;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.dto.attendance.AttendanceDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AttendanceService {

    @Transactional
    public AttendanceDTO attend(String auth0Id, Long eventId, AttendanceStatus status) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        // Vérification statut — uniquement les events PUBLISHED
        if (event.status != EventStatus.PUBLISHED) {
            throw new BadRequestException("Cannot attend a non-published event");
        }

        UUID userId = resolveUserId(auth0Id);

        // Vérification capacité
        if (event.capacity != null) {
            boolean alreadyAttending = Attendance.<Attendance>find(
                    "userId = ?1 and eventId = ?2",
                    userId, eventId)
                    .firstResultOptional()
                    .isPresent();
            if (!alreadyAttending) {
                long currentAttending = Attendance.count("eventId = ?1", eventId);
                if (currentAttending >= event.capacity) {
                    throw new WebApplicationException(
                            Response.status(Response.Status.CONFLICT)
                                    .entity(new ApiErrorResponse(
                                            "conflict", "Event has reached maximum capacity"))
                                    .type(MediaType.APPLICATION_JSON_TYPE)
                                    .build());
                }
            }
        }

        // Upsert : mettre à jour si existe, créer sinon
        Attendance attendance = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", userId, eventId)
                .firstResultOptional()
                .orElse(null);

        if (attendance == null) {
            attendance = new Attendance();
            attendance.userId = userId;
            attendance.eventId = eventId;
            attendance.status = status;
            attendance.persist();
        } else {
            attendance.status = status;
        }

        return AttendanceDTO.from(attendance);
    }

    @Transactional
    public void removeAttendance(String auth0Id, Long eventId) {
        UUID userId = resolveUserId(auth0Id);

        Attendance attendance = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", userId, eventId)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Attendance not found"));

        attendance.delete();
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
