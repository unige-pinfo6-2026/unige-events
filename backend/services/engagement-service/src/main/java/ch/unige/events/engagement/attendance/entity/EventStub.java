package ch.unige.events.engagement.attendance.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only stub of the Event entity. attendance-service needs:
 *
 * <ul>
 *   <li>{@code status} + {@code capacity} + {@code registrationDeadline}
 *       — gate for POST /attend.
 *   <li>{@code creator_id} — SCRUM-136 cascade for getAttendees.
 *   <li>The full EventDTO field set — getMyParticipationEvents enriches
 *       each event with the standard counts/availableSpots projection.
 * </ul>
 *
 * <p>Note: writable in attendance-service because the legacy
 * {@code attend} flow uses {@code entityManager.find(Event.class, id,
 * LockModeType.PESSIMISTIC_WRITE)} for capacity gating ; we never UPDATE
 * the event here, but the pessimistic lock requires a managed entity.
 *
 * <p>Replaced by REST client to event-service at PR 13.
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
    public EventCategory category;

    @Enumerated(EnumType.STRING)
    public Faculty faculty;

    public String bannerUrl;

    @Column(name = "creator_id")
    public UUID creatorId;

    @Enumerated(EnumType.STRING)
    public EventStatus status;

    public Integer capacity;

    @Column(nullable = false)
    public boolean allDay;

    @Column(nullable = false)
    public boolean featured;

    public LocalDateTime featuredAt;

    @Column(length = 500)
    public String websiteUrl;

    @Column(length = 255)
    public String contactEmail;

    public LocalDateTime registrationDeadline;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_tags", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "tag", nullable = false, length = 64)
    public List<String> tags = new ArrayList<>();

    @Column(name = "parent_event_id")
    public Long parentEventId;

    @Column(name = "recurrence_rule", length = 500)
    public String recurrenceRule;

    @Column(updatable = false)
    public LocalDateTime createdAt;

    public LocalDateTime updatedAt;
}
