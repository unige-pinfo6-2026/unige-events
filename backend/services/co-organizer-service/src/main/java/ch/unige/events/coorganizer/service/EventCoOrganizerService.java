package ch.unige.events.coorganizer.service;

import ch.unige.events.coorganizer.dto.ApiErrorResponse;
import ch.unige.events.coorganizer.dto.CoOrganizerDTO;
import ch.unige.events.coorganizer.dto.CoOrganizerInvitationDTO;
import ch.unige.events.coorganizer.dto.EventDTO;
import ch.unige.events.coorganizer.entity.AttendanceStatus;
import ch.unige.events.coorganizer.entity.AttendanceStub;
import ch.unige.events.coorganizer.entity.CoOrganizerStatus;
import ch.unige.events.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.coorganizer.entity.EventStub;
import ch.unige.events.coorganizer.entity.UserStub;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Same contract as the legacy EventCoOrganizerService. The bulk EventDTO
 * resolver used by {@link #getMyInvitations} reads from EventStub +
 * AttendanceStub directly until event-service / attendance-service ship
 * (PR 13 / 8).
 */
@ApplicationScoped
public class EventCoOrganizerService {

    @Inject EntityManager entityManager;

    @Transactional
    public CoOrganizerDTO invite(Long eventId, String inviterAuth0Id, UUID targetUserId, boolean isAdmin) {
        EventStub event = EventStub.<EventStub>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UserStub inviter = UserStub.findByAuth0Id(inviterAuth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        boolean creator = isCreator(event, inviter);
        if (!isAdmin && !creator) {
            throw new ForbiddenException("Only the event creator (or an admin) can invite co-organizers");
        }

        if (creator && inviter.id.equals(targetUserId)) {
            throw badRequest("cannot_invite_self",
                    "The event creator cannot invite themselves as co-organizer.");
        }

        UserStub target = UserStub.<UserStub>findByIdOptional(targetUserId)
                .orElseThrow(() -> new NotFoundException("Target user not found"));

        if (EventCoOrganizer.findByEventAndUser(eventId, targetUserId).isPresent()) {
            throw conflict("already_invited",
                    "This user already has a PENDING or ACCEPTED invitation on this event.");
        }

        EventCoOrganizer invitation = new EventCoOrganizer();
        invitation.eventId = eventId;
        invitation.userId = targetUserId;
        invitation.status = CoOrganizerStatus.PENDING;
        invitation.persist();

        return CoOrganizerDTO.from(invitation, target);
    }

    @Transactional
    public CoOrganizerDTO accept(Long eventId, String userAuth0Id) {
        UserStub user = UserStub.findByAuth0Id(userAuth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        EventCoOrganizer invitation = EventCoOrganizer.findByEventAndUser(eventId, user.id)
                .orElseThrow(() -> unprocessable("no_pending_invitation",
                        "No pending co-organizer invitation found for this event."));

        if (invitation.status != CoOrganizerStatus.ACCEPTED) {
            invitation.status = CoOrganizerStatus.ACCEPTED;
        }
        return CoOrganizerDTO.from(invitation, user);
    }

    @Transactional
    public void decline(Long eventId, String userAuth0Id) {
        UserStub user = UserStub.findByAuth0Id(userAuth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        EventCoOrganizer invitation = EventCoOrganizer.findByEventAndUser(eventId, user.id)
                .orElseThrow(() -> unprocessable("no_pending_invitation",
                        "No pending co-organizer invitation found for this event."));

        invitation.delete();
    }

    @Transactional
    public void remove(Long eventId, String requesterAuth0Id, UUID targetUserId, boolean isAdmin) {
        EventStub event = EventStub.<EventStub>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UserStub requester = UserStub.findByAuth0Id(requesterAuth0Id).orElse(null);
        if (!isAdmin && !isCreator(event, requester)) {
            throw new ForbiddenException("Only the event creator (or an admin) can remove co-organizers");
        }

        EventCoOrganizer.findByEventAndUser(eventId, targetUserId)
                .ifPresent(EventCoOrganizer::delete);
    }

    @Transactional
    public List<CoOrganizerDTO> getCoOrganizers(Long eventId) {
        EventStub.<EventStub>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        List<EventCoOrganizer> rows = EventCoOrganizer.findByEvent(eventId);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> userIds = rows.stream().map(r -> r.userId).distinct().toList();
        Map<UUID, UserStub> usersById = new HashMap<>();
        UserStub.<UserStub>list("id IN ?1", userIds).forEach(u -> usersById.put(u.id, u));

        return rows.stream()
                .map(r -> CoOrganizerDTO.from(r, usersById.get(r.userId)))
                .toList();
    }

    @Transactional
    public List<CoOrganizerInvitationDTO> getMyInvitations(String auth0Id, CoOrganizerStatus status, int page, int size) {
        UserStub user = UserStub.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        CoOrganizerStatus effective = status != null ? status : CoOrganizerStatus.PENDING;
        List<EventCoOrganizer> invitations = EventCoOrganizer.findByUser(user.id, effective, page, size);
        if (invitations.isEmpty()) {
            return List.of();
        }

        List<Long> eventIds = invitations.stream().map(i -> i.eventId).toList();
        Map<Long, EventDTO> eventsById = findByIdsAsDTO(eventIds);

        return invitations.stream()
                .map(i -> {
                    EventDTO event = eventsById.get(i.eventId);
                    return event != null ? CoOrganizerInvitationDTO.from(i, event) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * SCRUM-136 — Internal cascade lookup. Exposed through
     * {@code GET /events/{eventId}/co-organizers/check?userId=} for
     * comment-service / attendance-service / stats-service / event-service
     * once they migrate from local stubs to REST clients.
     */
    public boolean isAcceptedFor(Long eventId, UUID userId) {
        return EventCoOrganizer.isAcceptedFor(eventId, userId);
    }

    private Map<Long, EventDTO> findByIdsAsDTO(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<EventStub> events = EventStub.<EventStub>list("id IN ?1", ids);
        Map<Long, Long> attendingCounts = AttendanceStub.countGroupedByStatus(ids, AttendanceStatus.ATTENDING, entityManager);
        Map<Long, Long> waitlistedCounts = AttendanceStub.countGroupedByStatus(ids, AttendanceStatus.WAITLISTED, entityManager);
        Map<Long, EventDTO> result = new HashMap<>();
        for (EventStub event : events) {
            long att = attendingCounts.getOrDefault(event.id, 0L);
            long wait = waitlistedCounts.getOrDefault(event.id, 0L);
            EventDTO dto = EventDTO.from(event, att, computeAvailableSpots(event.capacity, att), wait, null, null);
            result.put(dto.id(), dto);
        }
        return result;
    }

    private static Long computeAvailableSpots(Integer capacity, long attendingCount) {
        if (capacity == null) {
            return null;
        }
        return Math.max(0L, capacity.longValue() - attendingCount);
    }

    private static boolean isCreator(EventStub event, UserStub user) {
        return event != null && user != null
                && event.creatorId != null
                && event.creatorId.equals(user.id);
    }

    protected static WebApplicationException badRequest(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    protected static WebApplicationException conflict(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    protected static WebApplicationException unprocessable(String error, String message) {
        return new WebApplicationException(
                Response.status(422)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }
}
