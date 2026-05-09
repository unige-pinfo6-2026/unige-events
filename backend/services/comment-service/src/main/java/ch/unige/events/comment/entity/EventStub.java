package ch.unige.events.comment.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Read-only stub of the Event entity. comment-service needs:
 *
 * <ul>
 *   <li>{@code status} — to enforce the comment-on-status branching
 *       (DRAFT/CANCELLED/EXPIRED → 400, BANNED → 404 anti-oracle).
 *   <li>{@code creator_id} — to detect the creator-cascade for DELETE
 *       (organizer can delete any comment on their event).
 * </ul>
 *
 * <p>Replaced by REST client to event-service at PR 13 :
 * {@code GET /events/{id}} returns the visibility-checked status + creator UUID.
 */
@Entity
@Table(name = "events")
public class EventStub extends PanacheEntity {

    @Enumerated(EnumType.STRING)
    public EventStatus status;

    @Column(name = "creator_id")
    public UUID creatorId;
}
