package ch.unige.events.report.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Writable stub of the Event entity. report-service writes
 * {@code event.status = BANNED} from {@link
 * ch.unige.events.report.service.ReportService#handle} (when an admin
 * REVIEWED a report) and from
 * {@link ch.unige.events.report.service.ModerationCleanupService#runCleanup}
 * (when PENDING reports cross the auto-hide threshold).
 *
 * <p>title is read by ReportDTO ; creator_id by the cascade SCRUM-136
 * "you can't report your own event" guard.
 *
 * <p>Replaced by REST clients to event-service at PR 13 :
 * the ban becomes a Kafka {@code events.banned} producer message
 * (consumed by event-service to flip the row), and the title +
 * creator_id reads become {@code GET /events/{id}}.
 */
@Entity
@Table(name = "events")
public class EventStub extends PanacheEntity {

    public String title;

    @Enumerated(EnumType.STRING)
    public EventStatus status;

    @Column(name = "creator_id")
    public UUID creatorId;
}
