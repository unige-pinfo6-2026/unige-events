package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_user",  columnList = "user_id"),
        @Index(name = "idx_notification_event", columnList = "event_id")
})
public class Notification extends PanacheEntity {

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    public NotificationType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String message;

    @Column(name = "event_id")
    public Long eventId;

    @Column(nullable = false)
    @ColumnDefault("false")
    public boolean read = false;

    @Column(name = "created_at", updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    // Unread first, then most recent — matches GET /notifications contract.
    public static List<Notification> findByUser(UUID userId, int page, int size) {
        return find("userId = ?1 order by read asc, createdAt desc, id desc", userId)
                .page(page, size)
                .list();
    }
}
