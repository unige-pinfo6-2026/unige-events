package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_event_creator", columnList = "creator_id"),
        @Index(name = "idx_event_start_date", columnList = "start_date"),
        @Index(name = "idx_event_faculty", columnList = "faculty"),
        @Index(name = "idx_event_featured_status_end", columnList = "featured, status, end_date"),
        @Index(name = "idx_event_parent", columnList = "parent_event_id")
})
public class Event extends PanacheEntity {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    public User creator;

    @Enumerated(EnumType.STRING)
    public EventStatus status = EventStatus.DRAFT;

    public Integer capacity;

    @Column(nullable = false)
    @ColumnDefault("false")
    public boolean allDay = false;

    @Column(nullable = false)
    @ColumnDefault("false")
    public boolean featured = false;

    public LocalDateTime featuredAt;

    @URL
    @Column(length = 500)
    public String websiteUrl;

    @Email
    @Column(length = 255)
    public String contactEmail;

    public LocalDateTime registrationDeadline;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "event_tags",
            joinColumns = @JoinColumn(name = "event_id"),
            foreignKey = @ForeignKey(name = "fk_event_tags_event")
    )
    @Column(name = "tag", nullable = false, length = 64)
    public List<String> tags = new ArrayList<>();

    @Column(unique = true)
    public String shareCode;

    @Column(name = "parent_event_id")
    public Long parentEventId;

    @Column(name = "recurrence_rule", length = 500)
    public String recurrenceRule;

    @Column(updatable = false)
    public LocalDateTime createdAt;

    public LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static Optional<Event> findByShareCode(String shareCode) {
        return find("shareCode", shareCode).firstResultOptional();
    }
}
