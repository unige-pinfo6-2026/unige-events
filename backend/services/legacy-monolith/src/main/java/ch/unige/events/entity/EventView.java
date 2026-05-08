package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

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

    /**
     * Timestamp of the most recent view of this event by this user. Refreshed on every
     * re-view via the atomic native upsert in {@link ch.unige.events.service.EventViewService}.
     */
    @Column(name = "viewed_at")
    public LocalDateTime viewedAt;

    @PrePersist
    public void prePersist() {
        viewedAt = LocalDateTime.now();
    }
}
