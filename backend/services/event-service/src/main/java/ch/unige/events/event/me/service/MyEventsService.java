package ch.unige.events.event.me.service;

import ch.unige.events.event.me.dto.EventDTO;
import ch.unige.events.event.entity.AttendanceStatus;
import ch.unige.events.event.entity.AttendanceStub;
import ch.unige.events.event.entity.EventStatus;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.entity.UserStub;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BFF endpoint for {@code GET /users/me/events} — events created by the
 * caller. Mirror of legacy {@code EventService.getMyEvents}.
 *
 * <p>Replaced by a REST call to event-service at PR 13 ({@code
 * GET /events?creatorId=}).
 */
@ApplicationScoped
public class MyEventsService {

    @Inject EntityManager entityManager;

    @Transactional
    public List<EventDTO> getMyEvents(String auth0Id, EventStatus status, int page, int size) {
        UserStub user = UserStub.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException(
                        "User profile not found — call GET /users/me first"));

        StringBuilder jpql = new StringBuilder("SELECT e FROM Event e WHERE e.creatorId = :creatorId");
        Map<String, Object> params = new HashMap<>();
        params.put("creatorId", user.id);
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
        Map<Long, Long> attendingCounts = AttendanceStub.countGroupedByStatus(eventIds, AttendanceStatus.ATTENDING, entityManager);
        Map<Long, Long> waitlistedCounts = AttendanceStub.countGroupedByStatus(eventIds, AttendanceStatus.WAITLISTED, entityManager);
        return events.stream()
                .map(e -> {
                    long att = attendingCounts.getOrDefault(e.id, 0L);
                    long wait = waitlistedCounts.getOrDefault(e.id, 0L);
                    return EventDTO.from(e, att, computeAvailableSpots(e.capacity, att), wait, null, null);
                })
                .toList();
    }

    private static Long computeAvailableSpots(Integer capacity, long attendingCount) {
        if (capacity == null) {
            return null;
        }
        return Math.max(0L, capacity.longValue() - attendingCount);
    }
}
