package ch.unige.events.report.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Owned by report-service. Carbon-copy of legacy
 * ch.unige.events.entity.Report — same FKs, same constraints, same
 * indexes. Associations point at the local stubs in this module.
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    public EventStub event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    public UserStub reporter;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    public UserStub reviewedBy;

    @Column(updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
