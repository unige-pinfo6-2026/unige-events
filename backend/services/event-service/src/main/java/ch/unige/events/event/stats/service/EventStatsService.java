package ch.unige.events.event.stats.service;

import ch.unige.events.event.stats.dto.EventStatsDTO;
import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.view.entity.EventView;
import ch.unige.events.event.favorite.entity.Favorite;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.projections.Auth0IdResolver;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.UUID;

/**
 * Caller UUID resolved from JWT claim, attending count delegated to
 * engagement-service via REST. Local Favorite + EventView entities
 * (no more local Stubs).
 */
@ApplicationScoped
public class EventStatsService {

    @Inject Instance<JsonWebToken> jwt;
    @Inject @RestClient EngagementServiceClient engagementClient;

    private JsonWebToken jwt() {
        return jwt.isResolvable() ? jwt.get() : null;
    }

    @Transactional
    public EventStatsDTO getStats(String auth0Id, Long eventId) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UUID callerUuid = Auth0IdResolver.resolveUserUuid(jwt());
        if (callerUuid == null) {
            throw new NotFoundException("User profile not found");
        }

        if (!isCreatorOrAcceptedCoOrganizer(event, callerUuid)) {
            throw new ForbiddenException("Only the event creator or an accepted co-organizer can view stats");
        }

        AttendanceSummary summary = engagementClient.getAttendanceSummary(eventId);
        long attendingCount = summary != null ? summary.attending() : 0L;
        long interestedCount = Favorite.count("eventId = ?1", eventId);
        long viewCount = EventView.count("eventId = ?1", eventId);

        return new EventStatsDTO(attendingCount, interestedCount, viewCount);
    }

    private static boolean isCreatorOrAcceptedCoOrganizer(Event event, UUID callerUuid) {
        if (event == null || callerUuid == null) {
            return false;
        }
        if (callerUuid.equals(event.creatorId)) {
            return true;
        }
        return EventCoOrganizer.isAcceptedFor(event.id, callerUuid);
    }
}
