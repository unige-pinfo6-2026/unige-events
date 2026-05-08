package ch.unige.events.view.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Read-only stub of the Event entity (just the PK column). view-service
 * needs to verify an event exists before recording a view ; full event
 * data lives in event-service (PR 13). Hibernate validate only checks
 * `id` exists in the events table — true today (legacy-monolith owns
 * the schema) and after PR 13 (event-service owns it).
 */
@Entity
@Table(name = "events")
public class EventStub extends PanacheEntity {
}
