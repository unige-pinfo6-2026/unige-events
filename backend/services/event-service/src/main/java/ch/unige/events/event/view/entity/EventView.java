package ch.unige.events.event.view.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Idempotent record of "user X viewed event Y" or "anonymous session S viewed event Y".
 * Owned by event-service (co-located post-finalization).
 *
 * <p>Post-V11 schema (2026-05-14): exactly one of (userId, sessionId) is populated.
 * Two normal UNIQUE constraints enforce dedup separately per branch:
 * <ul>
 *   <li>{@code uq_event_view_user_event}: UNIQUE(event_id, user_id) — rows with user_id NULL never collide here (Postgres NULL distinctness).</li>
 *   <li>{@code uq_event_view_event_session}: UNIQUE(event_id, session_id) — same trick for the anonymous branch.</li>
 * </ul>
 * A CHECK constraint {@code chk_event_view_user_or_session} forbids orphan rows where both columns are NULL.
 *
 * <p>We use full UNIQUE constraints (not partial unique indexes) because
 * PostgreSQL only recognizes real CONSTRAINTs as conflict targets for
 * {@code ON CONFLICT ... DO UPDATE}.
 */
@Entity
@Table(name = "event_views")
public class EventView extends PanacheEntity {

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Column(name = "user_id")
    public UUID userId;

    @Column(name = "session_id")
    public UUID sessionId;

    @Column(name = "viewed_at")
    public LocalDateTime viewedAt;

    @PrePersist
    public void prePersist() {
        viewedAt = LocalDateTime.now();
    }
}
