package ch.unige.events.report.entity;

import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Owned by moderation-service. Carbon-copy of legacy
 * ch.unige.events.entity.Report.
 *
 * <p>Décision F finalization-ultimate (STUB-001): cross-service
 * navigations to {@code event} / {@code reporter} / {@code reviewedBy}
 * are replaced by id-only columns. Underlying foreign keys still point
 * at {@code events(id)} / {@code users(id)} of the shared schema —
 * only JPA navigation goes away. Enrichment of event title / reporter
 * displayName is performed at the service layer via REST clients.
 */
@Entity
@Table(
        name = "reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_report_reporter_event", columnNames = {"reporter_id", "event_id"})
        },
        indexes = {
                @Index(name = "idx_report_event", columnList = "event_id"),
                @Index(name = "idx_report_status", columnList = "status")
        }
)
public class Report extends PanacheEntity {

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Column(name = "reporter_id")
    public UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public ReportReason reason;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(255)", nullable = false)
    public ReportStatus status = ReportStatus.PENDING;

    @Column(name = "moderation_note", columnDefinition = "TEXT")
    public String moderationNote;

    @Column(name = "reviewed_at")
    public LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    public UUID reviewedById;

    @Column(updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
