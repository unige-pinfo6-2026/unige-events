package ch.unige.events.favorite.entity;

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
 * Read-only stub of the Event entity. favorite-service needs to verify an
 * event exists before recording a favorite, and needs the full event record
 * to build EventDTO responses for {@code GET /users/me/favorites}. Full
 * Event entity and its lifecycle live in event-service (PR 13) — at that
 * point this stub is replaced by a REST client.
 *
 * <p>Hibernate validate only checks the columns declared here, so additional
 * columns owned by event-service (e.g. {@code share_code} owned by
 * share-service) remain invisible from favorite-service.
 *
 * <p>The {@code creator} relationship is replaced by a raw {@code creator_id}
 * UUID column to avoid pulling the full User entity graph cross-service —
 * we only need the creator UUID to populate {@link ch.unige.events.favorite.dto.EventDTO}.
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
