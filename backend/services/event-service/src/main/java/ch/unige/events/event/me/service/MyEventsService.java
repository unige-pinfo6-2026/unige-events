package ch.unige.events.event.me.service;

import ch.unige.events.shared.domain.projections.EventCapacity;
import ch.unige.events.event.me.dto.EventDTO;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.projections.Auth0IdResolver;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BFF endpoint for {@code GET /users/me/events} — events created by the
 * caller. Étape 3.4 finalization-ultimate (STUB-001 / Décisions F, I):
 * UserStub navigation replaced by JWT-claim resolution + bulk
 * attendance count delegated to engagement-service.
 */
@ApplicationScoped
public class MyEventsService {

    @Inject Instance<JsonWebToken> jwt;
    @Inject @RestClient EngagementServiceClient engagementClient;

    private JsonWebToken jwt() {
        return jwt.isResolvable() ? jwt.get() : null;
    }

    @Transactional
    public List<EventDTO> getMyEvents(String auth0Id, EventStatus status, int page, int size) {
        UUID userId = Auth0IdResolver.resolveUserUuid(jwt());
        if (userId == null) {
            throw new NotFoundException(
                    "User profile not found — call GET /users/me first");
        }

        StringBuilder jpql = new StringBuilder("SELECT e FROM Event e WHERE e.creatorId = :creatorId");
        Map<String, Object> params = new HashMap<>();
        params.put("creatorId", userId);
        if (status != null) {
            jpql.append(" AND e.status = :status");
            params.put("status", status);
        }
        jpql.append(" ORDER BY e.createdAt DESC, e.id DESC");

        List<Event> events = Event.<Event>find(jpql.toString(), params)
                .page(page, size)
                .list();

        return toEventDTOs(events);
    }

    private List<EventDTO> toEventDTOs(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> eventIds = events.stream().map(e -> e.id).toList();
        Map<Long, AttendanceSummary> summaries = engagementClient.getAttendanceSummariesBulk(eventIds);
        if (summaries == null) {
            summaries = Map.of();
        }
        Map<Long, AttendanceSummary> finalSummaries = summaries;
        return events.stream()
                .map(e -> {
                    AttendanceSummary s = finalSummaries.getOrDefault(
                            e.id, AttendanceSummary.of(0L, 0L));
                    long att = s.attending();
                    long wait = s.waitlisted();
                    return EventDTO.from(e, att, EventCapacity.computeAvailableSpots(e.capacity, att), wait, null, null);
                })
                .toList();
    }
}
