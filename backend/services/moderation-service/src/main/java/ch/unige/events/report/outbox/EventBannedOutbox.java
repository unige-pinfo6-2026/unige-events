package ch.unige.events.report.outbox;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox row for {@code events.banned} (Decision K, ADR-003). Persisted in
 * the same transaction as the {@code Report} status flip ; published
 * asynchronously by {@link EventBannedOutboxPoller} (at-least-once delivery).
 *
 * <p>Schema is owned by Flyway (V18__create_event_outbox.sql). The
 * {@code idx_event_banned_outbox_unpublished} partial index is created
 * by Flyway only — Hibernate {@code drop-and-create} (test profile) skips
 * it, which is fine because the table is empty in tests.
 */
@Entity
@Table(name = "event_banned_outbox")
public class EventBannedOutbox extends PanacheEntity {

    public Long eventId;

    public UUID bannedBy;

    public Instant occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    public String payloadJson;

    public Instant publishedAt;

    public int attempts;

    @Column(columnDefinition = "TEXT")
    public String lastError;

    @Column(updatable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
