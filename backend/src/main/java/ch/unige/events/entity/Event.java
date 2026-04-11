package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_event_creator", columnList = "creator_id"),
        @Index(name = "idx_event_start_date", columnList = "start_date")
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

    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "event_faculties", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "faculty")
    public List<Faculty> faculties = new ArrayList<>();

    public String bannerUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    public User creator;

    @Enumerated(EnumType.STRING)
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
