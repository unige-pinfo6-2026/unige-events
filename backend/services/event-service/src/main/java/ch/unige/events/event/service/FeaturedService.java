package ch.unige.events.event.service;

import ch.unige.events.event.dto.EventDTO;
import ch.unige.events.event.entity.AttendanceStatus;
import ch.unige.events.event.entity.AttendanceStub;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.entity.EventStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FeaturedService {

    @Inject EntityManager entityManager;

    @Transactional
    public List<EventDTO> getFeatured(int limit) {
        int effectiveLimit = Math.min(limit, 12);
        LocalDateTime now = LocalDateTime.now();

        List<Event> phase1 = entityManager.createQuery(
                "SELECT e FROM Event e WHERE e.featured = true AND e.status = :status " +
                "AND e.endDate >= :now ORDER BY e.featuredAt DESC, e.id ASC",
                Event.class)
                .setParameter("status", EventStatus.PUBLISHED)
                .setParameter("now", now)
                .setMaxResults(effectiveLimit)
                .getResultList();

        List<EventDTO> result = new ArrayList<>(toEventDTOs(phase1));

        if (result.size() < effectiveLimit) {
            int remaining = effectiveLimit - result.size();
            Set<Long> phase1Ids = phase1.stream().map(e -> e.id).collect(Collectors.toSet());

            StringBuilder jpql = new StringBuilder(
                    "SELECT e FROM Event e WHERE e.featured = false " +
                    "AND e.status = :status AND e.endDate >= :now");
            if (!phase1Ids.isEmpty()) {
                jpql.append(" AND e.id NOT IN :phase1Ids");
            }

            jakarta.persistence.TypedQuery<Event> q = entityManager
                    .createQuery(jpql.toString(), Event.class)
                    .setParameter("status", EventStatus.PUBLISHED)
                    .setParameter("now", now);
            if (!phase1Ids.isEmpty()) {
                q.setParameter("phase1Ids", phase1Ids);
            }
            List<Event> candidates = q.getResultList();

            if (!candidates.isEmpty()) {
                List<Long> candidateIds = candidates.stream().map(e -> e.id).toList();
                Map<Long, Long> attending = AttendanceStub.countGroupedByStatus(
                        candidateIds, AttendanceStatus.ATTENDING, entityManager);
                Map<Long, Long> favorites = countFavorites(candidateIds);

                List<Event> phase2 = candidates.stream()
                        .sorted(Comparator
                                .comparingLong((Event e) ->
                                        -(attending.getOrDefault(e.id, 0L) + favorites.getOrDefault(e.id, 0L)))
                                .thenComparingLong(e -> e.id))
                        .limit(remaining)
                        .toList();

                result.addAll(toEventDTOs(phase2, attending));
            }
        }

        return result;
    }

    @Transactional
    public EventDTO feature(Long id) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.featured) {
            event.featured = true;
            event.featuredAt = LocalDateTime.now();
        }
        return toSingleEventDTO(event);
    }

    @Transactional
    public EventDTO unfeature(Long id) {
        Event event = Event.<Event>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        event.featured = false;
        event.featuredAt = null;
        return toSingleEventDTO(event);
    }

    private EventDTO toSingleEventDTO(Event event) {
        long att = AttendanceStub.count("eventId = ?1 and status = ?2",
                event.id, AttendanceStatus.ATTENDING);
        long wait = AttendanceStub.count("eventId = ?1 and status = ?2",
                event.id, AttendanceStatus.WAITLISTED);
        return EventDTO.from(event, att,
                EventService.computeAvailableSpots(event.capacity, att), wait, null, null);
    }

    private List<EventDTO> toEventDTOs(List<Event> events) {
        if (events.isEmpty()) return List.of();
        List<Long> ids = events.stream().map(e -> e.id).toList();
        Map<Long, Long> attending = AttendanceStub.countGroupedByStatus(
                ids, AttendanceStatus.ATTENDING, entityManager);
        Map<Long, Long> waitlisted = AttendanceStub.countGroupedByStatus(
                ids, AttendanceStatus.WAITLISTED, entityManager);
        return events.stream().map(e -> {
            long att = attending.getOrDefault(e.id, 0L);
            long wait = waitlisted.getOrDefault(e.id, 0L);
            return EventDTO.from(e, att, EventService.computeAvailableSpots(e.capacity, att), wait, null, null);
        }).toList();
    }

    private List<EventDTO> toEventDTOs(List<Event> events, Map<Long, Long> attendingCounts) {
        if (events.isEmpty()) return List.of();
        List<Long> ids = events.stream().map(e -> e.id).toList();
        Map<Long, Long> waitlisted = AttendanceStub.countGroupedByStatus(
                ids, AttendanceStatus.WAITLISTED, entityManager);
        return events.stream().map(e -> {
            long att = attendingCounts.getOrDefault(e.id, 0L);
            long wait = waitlisted.getOrDefault(e.id, 0L);
            return EventDTO.from(e, att, EventService.computeAvailableSpots(e.capacity, att), wait, null, null);
        }).toList();
    }

    private Map<Long, Long> countFavorites(List<Long> eventIds) {
        if (eventIds.isEmpty()) return Map.of();
        List<Object[]> rows = entityManager.createQuery(
                "SELECT f.eventId, COUNT(f) FROM FavoriteStub f WHERE f.eventId IN :ids GROUP BY f.eventId",
                Object[].class)
                .setParameter("ids", eventIds)
                .getResultList();
        Map<Long, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((Long) row[0], (Long) row[1]);
        }
        return result;
    }
}
