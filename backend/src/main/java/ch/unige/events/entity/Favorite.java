package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(
    name = "favorites",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_favorite_user_event",
        columnNames = {"user_id", "event_id"}
    )
)
public class Favorite extends PanacheEntity {

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Column(updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public static Optional<Favorite> findByUserAndEvent(UUID userId, Long eventId) {
        return find("userId = ?1 and eventId = ?2", userId, eventId).firstResultOptional();
    }

    public static List<Favorite> findByUser(UUID userId, int page, int size) {
        return find("userId = ?1 order by createdAt desc", userId)
                .page(page, size)
                .list();
    }

    public static List<Favorite> findAllByUser(UUID userId) {
        return list("userId = ?1", userId);
    }
}
