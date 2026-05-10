package ch.unige.events.event.coorganizer.service;

import ch.unige.events.shared.domain.projections.EventCapacity;
import ch.unige.events.shared.error.ApiErrorResponse;
import ch.unige.events.event.coorganizer.dto.CoOrganizerDTO;
import ch.unige.events.event.coorganizer.dto.CoOrganizerInvitationDTO;
import ch.unige.events.event.coorganizer.dto.EventDTO;
import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.projections.Auth0IdResolver;
import ch.unige.events.shared.kafka.events.CoOrganizerEvent;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Same contract as the legacy EventCoOrganizerService. Étape 3.4
 * finalization-ultimate (STUB-001 / Décisions F, I): caller UUID via
 * JWT claim, target user enrichment via UserServiceClient, bulk
 * attendance counts via EngagementServiceClient.
 */
@ApplicationScoped
public class EventCoOrganizerService {

    @Inject jakarta.enterprise.event.Event<CoOrganizerEvent> coOrgEvent;
    @Inject Instance<JsonWebToken> jwt;

    @Inject @RestClient UserServiceClient userClient;
    @Inject @RestClient EngagementServiceClient engagementClient;

    private JsonWebToken jwt() {
        return jwt.isResolvable() ? jwt.get() : null;
    }

    @Transactional
    public CoOrganizerDTO invite(Long eventId, String inviterAuth0Id, UUID targetUserId, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UUID inviterId = Auth0IdResolver.resolveUserUuid(jwt());
        if (inviterId == null) {
            throw new NotFoundException("User profile not found — call GET /users/me first");
        }

        boolean creator = isCreator(event, inviterId);
        if (!isAdmin && !creator) {
            throw new ForbiddenException("Only the event creator (or an admin) can invite co-organizers");
        }

        if (creator && inviterId.equals(targetUserId)) {
            throw badRequest("cannot_invite_self",
                    "The event creator cannot invite themselves as co-organizer.");
        }

        UserPublicResponse target = lookupTargetUser(userClient, targetUserId, eventId);

        if (EventCoOrganizer.findByEventAndUser(eventId, targetUserId).isPresent()) {
            throw conflict("already_invited",
                    "This user already has a PENDING or ACCEPTED invitation on this event.");
        }

        EventCoOrganizer invitation = new EventCoOrganizer();
        invitation.eventId = eventId;
        invitation.userId = targetUserId;
        invitation.status = CoOrganizerStatus.PENDING;
        invitation.persist();

        // CDI fire — bridge publishes co-organizers.invited AFTER_SUCCESS.
        coOrgEvent.fire(CoOrganizerEvent.invited(eventId, targetUserId));

        return CoOrganizerDTO.from(invitation, target);
    }

    @Transactional
    public CoOrganizerDTO accept(Long eventId, String userAuth0Id) {
        UUID userId = Auth0IdResolver.resolveUserUuid(jwt());
        if (userId == null) {
            throw new NotFoundException("User profile not found — call GET /users/me first");
        }

        EventCoOrganizer invitation = EventCoOrganizer.findByEventAndUser(eventId, userId)
                .orElseThrow(() -> unprocessable("no_pending_invitation",
                        "No pending co-organizer invitation found for this event."));

        boolean transition = invitation.status != CoOrganizerStatus.ACCEPTED;
        if (transition) {
            invitation.status = CoOrganizerStatus.ACCEPTED;
            // CDI fire — bridge publishes co-organizers.accepted AFTER_SUCCESS.
            coOrgEvent.fire(CoOrganizerEvent.accepted(eventId, userId));
        }
        return CoOrganizerDTO.from(invitation, safeGetUser(userId));
    }

    @Transactional
    public void decline(Long eventId, String userAuth0Id) {
        UUID userId = Auth0IdResolver.resolveUserUuid(jwt());
        if (userId == null) {
            throw new NotFoundException("User profile not found — call GET /users/me first");
        }

        EventCoOrganizer invitation = EventCoOrganizer.findByEventAndUser(eventId, userId)
                .filter(c -> c.status == CoOrganizerStatus.PENDING)
                .orElseThrow(() -> unprocessable("no_pending_invitation",
                        "No PENDING invitation found for this user/event combination. " +
                        "Already accepted invitations must be removed via DELETE /events/{id}/co-organizers/{userId}."));

        invitation.delete();
    }

    @Transactional
    public void remove(Long eventId, String requesterAuth0Id, UUID targetUserId, boolean isAdmin) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UUID requesterId = Auth0IdResolver.resolveUserUuid(jwt());
        if (!isAdmin && !isCreator(event, requesterId)) {
            throw new ForbiddenException("Only the event creator (or an admin) can remove co-organizers");
        }

        EventCoOrganizer.findByEventAndUser(eventId, targetUserId)
                .ifPresent(EventCoOrganizer::delete);
    }

    @Transactional
    public List<CoOrganizerDTO> getCoOrganizers(Long eventId) {
        Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        List<EventCoOrganizer> rows = EventCoOrganizer.findByEvent(eventId);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> userIds = rows.stream().map(r -> r.userId).distinct().toList();
        Map<UUID, UserPublicResponse> usersById = new HashMap<>();
        for (UUID uid : userIds) {
            UserPublicResponse u = safeGetUser(uid);
            if (u != null) usersById.put(uid, u);
        }

        return rows.stream()
                .map(r -> CoOrganizerDTO.from(r, usersById.get(r.userId)))
                .toList();
    }

    @Transactional
    public List<CoOrganizerInvitationDTO> getMyInvitations(String auth0Id, CoOrganizerStatus status, int page, int size) {
        UUID userId = Auth0IdResolver.resolveUserUuid(jwt());
        if (userId == null) {
            throw new NotFoundException("User profile not found — call GET /users/me first");
        }

        CoOrganizerStatus effective = status != null ? status : CoOrganizerStatus.PENDING;
        List<EventCoOrganizer> invitations = EventCoOrganizer.findByUser(userId, effective, page, size);
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
     * SCRUM-136 — Internal cascade lookup. Exposed through the cross-
     * service {@code GET /events/{id}/organizer-uuids} endpoint
     * (Décision G).
     */
    public boolean isAcceptedFor(Long eventId, UUID userId) {
        return EventCoOrganizer.isAcceptedFor(eventId, userId);
    }

    /**
     * D19 (Étape 24.9.13) — distinguish "user not found" (404) from
     * "user-service unreachable" (503). NotFoundException propagates as
     * 404 ; any other RuntimeException (CB open, timeout, 5xx mapped to
     * RuntimeException) propagates as 503. A null return (typically the
     * {@code @Fallback} default on {@link UserServiceClient}) is treated
     * as 404 from the caller's perspective. Extracted as a static helper
     * so it can be unit-tested without the FT proxy that wraps the
     * injected {@link UserServiceClient}.
     */
    static UserPublicResponse lookupTargetUser(UserServiceClient client, UUID targetUserId, Long eventId) {
        UserPublicResponse target;
        try {
            target = client.getById(targetUserId);
        } catch (NotFoundException e) {
            throw new NotFoundException("Target user does not exist (or was deleted).");
        } catch (RuntimeException e) {
            Log.warnf(e, "[INVITE_USER_LOOKUP_FAIL] eventId=%d targetUserId=%s — user-service unreachable", eventId, targetUserId);
            throw new ServiceUnavailableException("User lookup is temporarily unavailable. Please retry.");
        }
        if (target == null) {
            throw new NotFoundException("Target user does not exist (or was deleted).");
        }
        return target;
    }

    private Map<Long, EventDTO> findByIdsAsDTO(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Event> events = Event.<Event>list("id IN ?1", ids);
        if (events.isEmpty()) {
            return Map.of();
        }
        Map<Long, AttendanceSummary> summaries = engagementClient.getAttendanceSummariesBulk(ids);
        if (summaries == null) {
            summaries = Map.of();
        }
        Map<Long, EventDTO> result = new HashMap<>();
        for (Event event : events) {
            AttendanceSummary s = summaries.getOrDefault(
                    event.id, AttendanceSummary.of(0L, 0L));
            long att = s.attending();
            long wait = s.waitlisted();
            EventDTO dto = EventDTO.from(event, att, EventCapacity.computeAvailableSpots(event.capacity, att), wait, null, null);
            result.put(dto.id(), dto);
        }
        return result;
    }

    private UserPublicResponse safeGetUser(UUID userId) {
        if (userId == null) {
            return null;
        }
        try {
            return userClient.getById(userId);
        } catch (jakarta.ws.rs.NotFoundException e) {
            // Semantic absence: user was hard-deleted or never existed. Caller
            // already treats null as "anonymous author" — no log needed.
            return null;
        } catch (RuntimeException e) {
            // Infra failure (timeout, CB open, JSON parse error, etc.). Log so
            // ops can correlate degraded enrichment to a downstream incident.
            Log.warnf(e, "[USER_ENRICHMENT_FAIL] safeGetUser(%s) — returning null (degraded enrichment due to downstream failure)", userId);
            return null;
        }
    }

    private static boolean isCreator(Event event, UUID userId) {
        return event != null && userId != null
                && event.creatorId != null
                && event.creatorId.equals(userId);
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
