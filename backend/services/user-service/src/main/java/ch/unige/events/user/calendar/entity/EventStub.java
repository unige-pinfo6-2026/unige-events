package ch.unige.events.user.calendar.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Read-only stub of the Event entity. calendar-service projects events
 * the user has favorited or registered to into an RFC 5545 ICS feed —
 * needs id (UID), title (SUMMARY), startDate / endDate (DTSTART/DTEND),
 * location (LOCATION), description (DESCRIPTION), and status (filtered
 * to PUBLISHED). Full Event entity lives in event-service (PR 13).
 */
@Entity
@Table(name = "events")
public class EventStub extends PanacheEntity {

    public String title;

    @Column(columnDefinition = "TEXT")
    public String description;

    public String location;

    public LocalDateTime startDate;

    public LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    public EventStatus status;
}
