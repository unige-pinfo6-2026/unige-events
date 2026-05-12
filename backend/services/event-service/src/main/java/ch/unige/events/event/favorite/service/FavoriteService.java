package ch.unige.events.event.favorite.service;

import ch.unige.events.shared.domain.projections.EventCapacity;
import ch.unige.events.event.favorite.dto.EventDTO;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.favorite.entity.Favorite;
import ch.unige.events.shared.client.EngagementServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.projections.Auth0IdResolver;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Same semantics as the legacy monolith's FavoriteService — idempotent
 * add, NotFoundException on missing event / favorite, listing returns
 * EventDTOs enriched with attending / waitlisted counts.
 *
 * <p>Caller UUID resolved via JWT claim, attendance counts via REST client
 * to engagement-service.
 */
@ApplicationScoped
public class FavoriteService {

    @Inject Instance<JsonWebToken> jwt;
    @Inject @RestClient EngagementServiceClient engagementClient;
    @Inject EntityManager entityManager;

    private JsonWebToken jwt() {
        return jwt.isResolvable() ? jwt.get() : null;
    }

    @Transactional
    public void addFavorite(String auth0Id, Long eventId) {
        Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UUID userId = resolveUserId();

        // BUG-006-bis: tolerate concurrent double-tap idempotently. The
        // pre-check covers the common idempotent path; the try / flush /
        // unique-constraint catch handles the race where two transactions
        // both observe alreadyExists=false and both reach persist(). The
        // unique constraint uq_favorite_user_event on (user_id, event_id)
        // turns the loser INSERT into a noop. Aligns with the FollowService
        // pattern referenced by the spec (Décision B alternative path).
        if (Favorite.findByUserAndEvent(userId, eventId).isPresent()) {
            return;
        }
        try {
            Favorite favorite = new Favorite();
            favorite.userId = userId;
            favorite.eventId = eventId;
            favorite.persist();
            entityManager.flush();
        } catch (PersistenceException e) {
            // match the unique-violation by constraint name rather than by
            // exception type alone. The previous
            // isUniqueConstraintViolation() swallowed *any*
            // ConstraintViolationException — including unrelated unique
            // constraints introduced by future migrations on this table
            // (e.g. a hypothetical NOT NULL on user_id that would surface
            // here as a ConstraintViolationException of a different
            // category). Matching uq_favorite_user_event by name keeps the
            // idempotent-double-tap path narrowly scoped and re-throws
            // anything else so it surfaces as a 5xx.
            if (!isUniqueFavoriteConflict(e)) {
                throw e;
            }
            // Concurrent insert won — idempotent noop, the favorite exists.
            // Log at DEBUG: double-tap is expected (mobile clients firing
            // two requests on a fast tap) and benign; logging at WARN/ERROR
            // would pollute the SLO error budget for normal user behavior.
            Log.debugf("[FAVORITE_DOUBLE_TAP] idempotent noop user=%s event=%d", userId, eventId);
        }
    }

    private static boolean isUniqueFavoriteConflict(PersistenceException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException c) {
                String name = c.getConstraintName();
                if (name != null && name.equalsIgnoreCase("uq_favorite_user_event")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Transactional
    public void removeFavorite(String auth0Id, Long eventId) {
        UUID userId = resolveUserId();

        Favorite favorite = Favorite.findByUserAndEvent(userId, eventId)
                .orElseThrow(() -> new NotFoundException("Favorite not found"));

        favorite.delete();
    }

    @Transactional
    public List<EventDTO> getFavorites(String auth0Id, int page, int size) {
        UUID userId = resolveUserId();

        List<Favorite> favorites = Favorite.findByUser(userId, page, size);
        if (favorites.isEmpty()) {
            return List.of();
        }
        List<Long> eventIds = favorites.stream().map(f -> f.eventId).toList();
        Map<Long, AttendanceSummary> summaries = engagementClient.getAttendanceSummariesBulk(eventIds);
        if (summaries == null) {
            summaries = Map.of();
        }
        Map<Long, AttendanceSummary> finalSummaries = summaries;
        // bulk-fetch all favorited events in a single SELECT instead of
        // issuing one findByIdOptional per favorite (N+1).
        Map<Long, Event> eventsById = Event.<Event>list("id IN ?1", eventIds).stream()
                .collect(Collectors.toMap(e -> e.id, e -> e));
        return favorites.stream()
                .map(f -> eventsById.get(f.eventId))
                .filter(Objects::nonNull)
                .map(e -> {
                    AttendanceSummary s = finalSummaries.getOrDefault(
                            e.id, AttendanceSummary.of(0L, 0L));
                    long att = s.attending();
                    long wait = s.waitlisted();
                    return EventDTO.from(e, att, EventCapacity.computeAvailableSpots(e.capacity, att), wait, null, null);
                })
                .toList();
    }

    private UUID resolveUserId() {
        UUID userId = Auth0IdResolver.resolveUserUuid(jwt());
        if (userId == null) {
            throw new NotFoundException("User profile not found");
        }
        return userId;
    }
}
