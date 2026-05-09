package ch.unige.events.comment.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.List;
import java.util.UUID;

/**
 * Read-only stub of the EventCoOrganizer entity. comment-service uses it
 * for the SCRUM-136 cascade : an ACCEPTED co-organizer can delete any
 * comment on the event they co-organize, and is shown as
 * {@code authorIsOrganizer = true} in the listing. Replaced by REST
 * client to co-organizer-service at PR 7
 * ({@code GET /events/{id}/co-organizers/check?userId=}).
 */
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

    public static List<UUID> findAcceptedUserIdsForEvent(Long eventId) {
        return getEntityManager()
                .createQuery(
                        "select c.userId from EventCoOrganizerStub c " +
                        "where c.eventId = :eventId and c.status = :status",
                        UUID.class)
                .setParameter("eventId", eventId)
                .setParameter("status", CoOrganizerStatus.ACCEPTED)
                .getResultList();
    }
}
