package ch.unige.events.stats.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(
    name = "event_co_organizers",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_event_co_organizers_event_user",
        columnNames = {"event_id", "user_id"}
    )
)
public class EventCoOrganizerStub extends PanacheEntity {

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CoOrganizerStatus status;

    public static boolean isAcceptedFor(Long eventId, UUID userId) {
        return count("eventId = ?1 and userId = ?2 and status = ?3",
                eventId, userId, CoOrganizerStatus.ACCEPTED) > 0;
    }
}
