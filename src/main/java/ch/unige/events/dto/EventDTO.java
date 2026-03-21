package ch.unige.events.dto;

import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;

import java.time.LocalDateTime;

public record EventDTO(
        Long id,
        String title,
        String description,
        String location,
        LocalDateTime startDate,
        LocalDateTime endDate,
        EventCategory category,
        String imageUrl,
        Long organizerId,
        EventStatus status,
        Integer capacity,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
