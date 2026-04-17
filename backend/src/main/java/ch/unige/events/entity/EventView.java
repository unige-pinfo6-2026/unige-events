package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Optional;
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

    @Column(name = "viewed_at", updatable = false)
    public LocalDateTime viewedAt;

    @PrePersist
    public void prePersist() {
        viewedAt = LocalDateTime.now();
    }

    public static Optional<EventView> findByEventAndUser(Long eventId, UUID userId) {
        return find("eventId = ?1 and userId = ?2", eventId, userId).firstResultOptional();
    }
}
