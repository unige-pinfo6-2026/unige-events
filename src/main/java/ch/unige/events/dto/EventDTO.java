package ch.unige.events.dto;

import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;

import java.time.LocalDateTime;

public class EventDTO {
    public Long id;
    public String title;
    public String description;
    public String location;
    public LocalDateTime startDate;
    public LocalDateTime endDate;
    public EventCategory category;
    public String imageUrl;
    public Long organizerId;
    public EventStatus status;
    public Integer capacity;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
