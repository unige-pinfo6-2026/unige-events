package ch.unige.events.coorganizer.entity;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owned by co-organizer-service. Carbon-copy of legacy
 * ch.unige.events.entity.EventCoOrganizer — same constraint, same indexes,
 * same finders.
 */
@Entity
@Table(
    name = "event_co_organizers",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_event_co_organizers_event_user",
        columnNames = {"event_id", "user_id"}
    ),
    indexes = {
        @Index(name = "idx_event_co_organizers_event", columnList = "event_id"),
        @Index(name = "idx_event_co_organizers_user", columnList = "user_id")
    }
)
public class EventCoOrganizer extends PanacheEntity {

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CoOrganizerStatus status;

    @Column(updatable = false)
    public LocalDateTime invitedAt;

    @PrePersist
    public void prePersist() {
        if (invitedAt == null) {
            invitedAt = LocalDateTime.now();
        }
    }

    public static boolean isAcceptedFor(Long eventId, UUID userId) {
        return count("eventId = ?1 and userId = ?2 and status = ?3",
                eventId, userId, CoOrganizerStatus.ACCEPTED) > 0;
    }

    public static Optional<EventCoOrganizer> findByEventAndUser(Long eventId, UUID userId) {
        return find("eventId = ?1 and userId = ?2", eventId, userId).firstResultOptional();
    }

    public static List<EventCoOrganizer> findByEvent(Long eventId) {
        return list("eventId = ?1 order by invitedAt asc", eventId);
    }

    public static List<EventCoOrganizer> findByUser(UUID userId, CoOrganizerStatus status, int page, int size) {
        return find("userId = ?1 and status = ?2 order by invitedAt desc", userId, status)
                .page(page, size)
                .list();
    }
}
