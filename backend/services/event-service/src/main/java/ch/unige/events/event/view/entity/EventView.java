package ch.unige.events.event.view.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Idempotent record of "user X viewed event Y". Owned by view-service.
 * Carbon-copy of the legacy monolith's
 * ch.unige.events.entity.EventView entity — same table, same constraints.
 */
@Entity
@Table(
    name = "event_views",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_event_view_user_event",
        columnNames = {"event_id", "user_id"}
    )
)
public class EventView extends PanacheEntity {

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "viewed_at")
    public LocalDateTime viewedAt;

    @PrePersist
    public void prePersist() {
        viewedAt = LocalDateTime.now();
    }
}
