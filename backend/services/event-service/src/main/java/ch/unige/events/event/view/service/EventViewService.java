package ch.unige.events.event.view.service;

import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.projections.CallerIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Idempotent recording of "user X viewed event Y" — uses an upsert via
 * native SQL with ON CONFLICT, identical semantics to the legacy
 * monolith's EventViewService. Refreshing viewed_at gives "most recent
 * view" semantics.
 *
 * <p>Post-V11 (2026-05-14): also handles anonymous views via a client-generated
 * sessionId UUID (persisted in browser localStorage). The dedup key is
 * {@code (event_id, session_id)} for anonymous callers, with a partial unique
 * index ensuring uniqueness only when {@code session_id IS NOT NULL}. If
 * neither auth0Id nor sessionId is provided, the call is a silent no-op
 * (the event existence check still runs and may throw 404).
 *
 * <p>Caller UUID (when authenticated) is resolved via CallerIdentity, with no
 * {@code UserStub.findByAuth0Id} cross-schema query.
 */
@ApplicationScoped
public class EventViewService {

    private final EntityManager entityManager;
    private final CallerIdentity callerIdentity;

    public EventViewService(EntityManager entityManager, CallerIdentity callerIdentity) {
        this.entityManager = entityManager;
        this.callerIdentity = callerIdentity;
    }

    /**
     * Records a view of {@code eventId}. Idempotent per branch.
     *
     * @param auth0Id   the authenticated caller's auth0 sub, or {@code null} for anon
     * @param eventId   the event whose view is recorded
     * @param sessionId the client-generated anonymous session UUID, or {@code null}
     * @throws NotFoundException if the event does not exist
     */
    @Transactional
    public void recordView(String auth0Id, Long eventId, UUID sessionId) {
        if (entityManager.find(Event.class, eventId) == null) {
            throw new NotFoundException("Event not found");
        }

        if (auth0Id != null) {
            // Authenticated path: resolve the caller's internal UUID and upsert
            // on (event_id, user_id). callerIdentity.getUuid() returns null if
            // the caller has no provisioned profile yet (rare race) — we treat
            // that as anonymous fallback.
            UUID userId = callerIdentity.getUuid();
            if (userId != null) {
                upsertAuthenticated(eventId, userId);
                return;
            }
        }

        if (sessionId != null) {
            upsertAnonymous(eventId, sessionId);
        }
        // else: silent no-op (caller has no JWT and no session UUID).
    }

    private void upsertAuthenticated(Long eventId, UUID userId) {
        // ON CONFLICT target = unique constraint uq_event_view_user_event.
        // No WHERE clause: PostgreSQL only recognizes real CONSTRAINTs (not
        // partial indexes) for ON CONFLICT, so V11 was migrated to full
        // UNIQUE constraints. NULL distinctness ensures rows with user_id
        // NULL never collide here.
        entityManager.createNativeQuery(
                "INSERT INTO event_views (id, event_id, user_id, viewed_at) " +
                "VALUES (nextval('event_views_seq'), :eventId, :userId, :viewedAt) " +
                "ON CONFLICT (event_id, user_id) " +
                "DO UPDATE SET viewed_at = EXCLUDED.viewed_at")
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .setParameter("viewedAt", LocalDateTime.now(ZoneId.systemDefault()))
                .executeUpdate();
    }

    private void upsertAnonymous(Long eventId, UUID sessionId) {
        // ON CONFLICT target = unique constraint uq_event_view_event_session.
        entityManager.createNativeQuery(
                "INSERT INTO event_views (id, event_id, session_id, viewed_at) " +
                "VALUES (nextval('event_views_seq'), :eventId, :sessionId, :viewedAt) " +
                "ON CONFLICT (event_id, session_id) " +
                "DO UPDATE SET viewed_at = EXCLUDED.viewed_at")
                .setParameter("eventId", eventId)
                .setParameter("sessionId", sessionId)
                .setParameter("viewedAt", LocalDateTime.now(ZoneId.systemDefault()))
                .executeUpdate();
    }
}
