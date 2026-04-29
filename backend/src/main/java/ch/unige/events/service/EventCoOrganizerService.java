package ch.unige.events.service;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.dto.coorganizer.CoOrganizerDTO;
import ch.unige.events.dto.coorganizer.CoOrganizerInvitationDTO;
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCoOrganizer;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

@ApplicationScoped
public class EventCoOrganizerService {

    @Inject
    EventService eventService;

    @Transactional
    public CoOrganizerDTO invite(Long eventId, String inviterAuth0Id, UUID targetUserId, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        User inviter = User.findByAuth0Id(inviterAuth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        boolean creator = isCreator(event, inviterAuth0Id);
        if (!isAdmin && !creator) {
            throw new ForbiddenException("Only the event creator (or an admin) can invite co-organizers");
        }

        // Auto-invitation interdite : si l'appelant est le créateur et tente de s'inviter lui-même.
        // La garde s'applique aussi quand l'admin = créateur (admin du système qui possède aussi l'event).
        if (creator && inviter.id.equals(targetUserId)) {
            throw badRequest("cannot_invite_self",
                    "The event creator cannot invite themselves as co-organizer.");
        }

        User target = User.<User>findByIdOptional(targetUserId)
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
        User user = User.findByAuth0Id(userAuth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        EventCoOrganizer invitation = EventCoOrganizer.findByEventAndUser(eventId, user.id)
                .orElseThrow(NotFoundException::new);

        if (invitation.status != CoOrganizerStatus.ACCEPTED) {
            invitation.status = CoOrganizerStatus.ACCEPTED;
        }
        return CoOrganizerDTO.from(invitation, user);
    }

    @Transactional
    public void decline(Long eventId, String userAuth0Id) {
        User user = User.findByAuth0Id(userAuth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        EventCoOrganizer invitation = EventCoOrganizer.findByEventAndUser(eventId, user.id)
                .orElseThrow(NotFoundException::new);

        invitation.delete();
    }

    @Transactional
    public void remove(Long eventId, String requesterAuth0Id, UUID targetUserId, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        if (!isAdmin && !isCreator(event, requesterAuth0Id)) {
            throw new ForbiddenException("Only the event creator (or an admin) can remove co-organizers");
        }

        EventCoOrganizer.findByEventAndUser(eventId, targetUserId)
                .ifPresent(EventCoOrganizer::delete);
    }

    @Transactional
    public List<CoOrganizerDTO> getCoOrganizers(Long eventId) {
        // Vérifie l'existence de l'event — 404 si absent (cohérent avec l'OpenAPI).
        Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        List<EventCoOrganizer> rows = EventCoOrganizer.findByEvent(eventId);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> userIds = rows.stream().map(r -> r.userId).distinct().toList();
        Map<UUID, User> usersById = new HashMap<>();
        User.<User>list("id IN ?1", userIds).forEach(u -> usersById.put(u.id, u));

        return rows.stream()
                .map(r -> CoOrganizerDTO.from(r, usersById.get(r.userId)))
                .toList();
    }

    @Transactional
    public List<CoOrganizerInvitationDTO> getMyInvitations(String auth0Id, CoOrganizerStatus status, int page, int size) {
        User user = User.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

        CoOrganizerStatus effective = status != null ? status : CoOrganizerStatus.PENDING;
        List<EventCoOrganizer> invitations = EventCoOrganizer.findByUser(user.id, effective, page, size);
        if (invitations.isEmpty()) {
            return List.of();
        }

        List<Long> eventIds = invitations.stream().map(i -> i.eventId).toList();
        Map<Long, EventDTO> eventsById = eventService.findByIdsAsDTO(eventIds);

        return invitations.stream()
                .map(i -> {
                    EventDTO event = eventsById.get(i.eventId);
                    return event != null ? CoOrganizerInvitationDTO.from(i, event) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static boolean isCreator(Event event, String auth0Id) {
        return event.creator != null
                && event.creator.auth0Id != null
                && event.creator.auth0Id.equals(auth0Id);
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
}
