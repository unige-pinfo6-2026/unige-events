package ch.unige.events.dto.event;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventDTO(
        Long id,
        String title,
        String description,
        String location,
        LocalDateTime startDate,
        LocalDateTime endDate,
        EventCategory category,
        String bannerUrl,
        UUID creatorId,
        EventStatus status,
        Integer capacity,
        long attendingCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EventDTO from(Event event, long attendingCount) {
        return new EventDTO(
                event.id,
                event.title,
                event.description,
                event.location,
                event.startDate,
                event.endDate,
                event.category,
                event.bannerUrl,
                event.creator != null ? event.creator.id : null,
                event.status,
                event.capacity,
                attendingCount,
                event.createdAt,
                event.updatedAt
        );
    }
}
