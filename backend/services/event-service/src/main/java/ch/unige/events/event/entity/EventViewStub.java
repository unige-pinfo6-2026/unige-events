package ch.unige.events.event.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(
    name = "event_views",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_event_view_user_event",
        columnNames = {"event_id", "user_id"}
    )
)
public class EventViewStub extends PanacheEntity {

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Column(name = "user_id", nullable = false)
    public UUID userId;
}
