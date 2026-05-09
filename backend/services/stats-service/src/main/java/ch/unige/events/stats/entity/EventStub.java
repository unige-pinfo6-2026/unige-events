package ch.unige.events.stats.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Read-only stub of the Event entity. stats-service needs creator_id for
 * the SCRUM-136 cascade ("only the creator OR an ACCEPTED co-organizer
 * can view stats"). Replaced by REST client to event-service at PR 13.
 */
@Entity
@Table(name = "events")
public class EventStub extends PanacheEntity {

    @Column(name = "creator_id")
    public UUID creatorId;
}
