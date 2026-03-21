package ch.unige.events.dto;

import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;

import java.time.LocalDateTime;

public class UpdateEventRequest {
    public String title;
    public String description;
    public String location;
    public LocalDateTime startDate;
    public LocalDateTime endDate;
    public EventCategory category;
    public String imageUrl;
    public Integer capacity;
    public EventStatus status;
}
