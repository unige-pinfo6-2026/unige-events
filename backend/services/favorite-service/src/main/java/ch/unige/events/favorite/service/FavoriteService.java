package ch.unige.events.favorite.service;

import ch.unige.events.favorite.dto.EventDTO;
import ch.unige.events.favorite.entity.AttendanceStatus;
import ch.unige.events.favorite.entity.AttendanceStub;
import ch.unige.events.favorite.entity.EventStub;
import ch.unige.events.favorite.entity.Favorite;
import ch.unige.events.favorite.entity.UserStub;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Same semantics as the legacy monolith's FavoriteService — idempotent
 * add, NotFoundException on missing event / favorite, listing returns
 * EventDTOs enriched with attending / waitlisted counts.
 */
@ApplicationScoped
public class FavoriteService {

    @Inject
    EntityManager entityManager;

    @Transactional
    public void addFavorite(String auth0Id, Long eventId) {
        EventStub.<EventStub>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UUID userId = resolveUserId(auth0Id);

        boolean alreadyExists = Favorite.findByUserAndEvent(userId, eventId).isPresent();
        if (alreadyExists) {
            return;
        }

        Favorite favorite = new Favorite();
        favorite.userId = userId;
        favorite.eventId = eventId;
        favorite.persist();
    }

    @Transactional
    public void removeFavorite(String auth0Id, Long eventId) {
        UUID userId = resolveUserId(auth0Id);

        Favorite favorite = Favorite.findByUserAndEvent(userId, eventId)
                .orElseThrow(() -> new NotFoundException("Favorite not found"));

        favorite.delete();
    }

    @Transactional
    public List<EventDTO> getFavorites(String auth0Id, int page, int size) {
        UUID userId = resolveUserId(auth0Id);

        List<Favorite> favorites = Favorite.findByUser(userId, page, size);
        List<Long> eventIds = favorites.stream().map(f -> f.eventId).toList();
        Map<Long, Long> attendingCounts = AttendanceStub.countGroupedByStatus(eventIds, AttendanceStatus.ATTENDING, entityManager);
        Map<Long, Long> waitlistedCounts = AttendanceStub.countGroupedByStatus(eventIds, AttendanceStatus.WAITLISTED, entityManager);
        return favorites.stream()
                .map(f -> EventStub.<EventStub>findByIdOptional(f.eventId))
                .flatMap(Optional::stream)
                .map(e -> {
                    long att = attendingCounts.getOrDefault(e.id, 0L);
                    long wait = waitlistedCounts.getOrDefault(e.id, 0L);
                    return EventDTO.from(e, att, computeAvailableSpots(e.capacity, att), wait, null, null);
                })
                .toList();
    }

    /**
     * Inlined from {@code EventService.computeAvailableSpots} — capacity null
     * means unlimited (returns null), otherwise capacity minus current
     * attending count clamped to zero.
     */
    static Long computeAvailableSpots(Integer capacity, long attendingCount) {
        if (capacity == null) {
            return null;
        }
        return Math.max(0L, capacity.longValue() - attendingCount);
    }

    private UUID resolveUserId(String auth0Id) {
        return UserStub.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException("User profile not found"))
                .id;
    }
}
