package ch.unige.events.event.stats.service;

import ch.unige.events.event.stats.dto.EventStatsDTO;
import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.view.entity.EventView;
import ch.unige.events.event.favorite.entity.Favorite;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.projections.CallerIdentity;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.UUID;

/**
 * Caller UUID resolved via CallerIdentity, attending count delegated to
 * engagement-service via REST. Local Favorite + EventView entities
 * (no more local Stubs).
 */
@ApplicationScoped
public class EventStatsService {

    private static final String ROLE_ADMIN = "ADMIN";

    @Inject CallerIdentity callerIdentity;
    @Inject SecurityIdentity identity;
    @Inject @RestClient EngagementServiceClient engagementClient;

    @Transactional
    public EventStatsDTO getStats(String auth0Id, Long eventId) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UUID callerUuid = callerIdentity.requireUuid();

        if (!isAuthorizedToViewStats(event, callerUuid)) {
            throw new ForbiddenException("Only the event creator, an accepted co-organizer, or an admin can view stats");
        }

        AttendanceSummary summary = engagementClient.getAttendanceSummary(eventId);
        long attendingCount = summary != null ? summary.attending() : 0L;
        long interestedCount = Favorite.count("eventId = ?1", eventId);
        long viewCount = EventView.count("eventId = ?1", eventId);

        return new EventStatsDTO(attendingCount, interestedCount, viewCount);
    }

    /**
     * Authorises a caller to view event statistics. Site admins (Auth0 role
     * {@code ADMIN}) are granted access regardless of their relationship to
     * the event; otherwise the caller must be the creator or an
     * {@link ch.unige.events.shared.domain.enums.CoOrganizerStatus#ACCEPTED}
     * co-organizer. Not {@code static} on purpose: needs access to the
     * injected {@link SecurityIdentity} to read the role claim.
     */
    private boolean isAuthorizedToViewStats(Event event, UUID callerUuid) {
        if (event == null || callerUuid == null) {
            return false;
        }
        if (identity.hasRole(ROLE_ADMIN)) {
            return true;
        }
        if (callerUuid.equals(event.creatorId)) {
            return true;
        }
        return EventCoOrganizer.isAcceptedFor(event.id, callerUuid);
    }
}
