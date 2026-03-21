package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_event_organizer", columnList = "organizerId"),
        @Index(name = "idx_event_start_date", columnList = "startDate")
})
public class Event extends PanacheEntity {

    @NotBlank
    public String title;

    public String description;

    @NotBlank
    public String location;

    @NotNull
    @Future
    public LocalDateTime startDate;

    @NotNull
    public LocalDateTime endDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    public EventCategory category;

    public String imageUrl;

    @NotNull
    public Long organizerId;

    @NotNull
    @Enumerated(EnumType.STRING)
    public EventStatus status = EventStatus.DRAFT;

    public Integer capacity;

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
}
