package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Optional;

@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_event_creator", columnList = "creator_id"),
        @Index(name = "idx_event_start_date", columnList = "start_date"),
        @Index(name = "idx_event_faculty", columnList = "faculty")
})
public class Event extends PanacheEntity {

    public String title;

    @Column(columnDefinition = "TEXT")
    public String description;

    public String location;

    public LocalDateTime startDate;

    public LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(255)")
    public EventCategory category;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(255)")
    public Faculty faculty;

    public String bannerUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    public User creator;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(255)")
    public EventStatus status = EventStatus.DRAFT;

    public Integer capacity;

    @Column(unique = true)
    public String shareCode;

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
