package ch.unige.events.event.view.service;

import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.projections.Auth0IdResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Idempotent recording of "user X viewed event Y" — uses an upsert via
 * native SQL with ON CONFLICT, identical semantics to the legacy
 * monolith's EventViewService. Refreshing viewed_at gives "most recent
 * view" semantics.
 *
 * <p>Caller UUID is resolved from the JWT {@code uuid} claim
 * ({@link Auth0IdResolver#resolveUserUuid}) — no more
 * {@code UserStub.findByAuth0Id} cross-schema query.
 */
@ApplicationScoped
public class EventViewService {

    private final EntityManager entityManager;

    @Inject Instance<JsonWebToken> jwt;

    public EventViewService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    private JsonWebToken jwt() {
        return jwt.isResolvable() ? jwt.get() : null;
    }

    @Transactional
    public void recordView(String auth0Id, Long eventId) {
        if (entityManager.find(Event.class, eventId) == null) {
            throw new NotFoundException("Event not found");
        }

        UUID userId = Auth0IdResolver.resolveUserUuid(jwt());
        if (userId == null) {
            throw new NotFoundException("User profile not found");
        }

        // Native upsert: ON CONFLICT closes the concurrent-insert race.
        // event_views_seq is created by Hibernate's drop-and-create in test
        // and pre-existing in legacy-monolith's V5__create_event_views.sql
        // for prod.
        entityManager.createNativeQuery(
                "INSERT INTO event_views (id, event_id, user_id, viewed_at) " +
                "VALUES (nextval('event_views_seq'), :eventId, :userId, :viewedAt) " +
                "ON CONFLICT (event_id, user_id) DO UPDATE SET viewed_at = EXCLUDED.viewed_at")
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .setParameter("viewedAt", LocalDateTime.now())
                .executeUpdate();
    }
}
