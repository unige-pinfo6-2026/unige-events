package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

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
    public Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    public User reporter;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(255)", nullable = false)
    public ReportStatus status = ReportStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    public String reason;

    @Column(updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
