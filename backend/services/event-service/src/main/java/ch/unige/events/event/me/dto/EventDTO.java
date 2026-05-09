package ch.unige.events.event.me.dto;

import ch.unige.events.event.entity.EventCategory;
import ch.unige.events.event.entity.EventStatus;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.entity.Faculty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EventDTO(
        Long id,
        String title,
        String description,
        String location,
        LocalDateTime startDate,
        LocalDateTime endDate,
        EventCategory category,
        Faculty faculty,
        String bannerUrl,
        UUID creatorId,
        EventStatus status,
        Integer capacity,
        boolean allDay,
        boolean featured,
        LocalDateTime featuredAt,
        long attendingCount,
        Long availableSpots,
        long waitlistedCount,
        Long viewCount,
        Long interestedCount,
        String websiteUrl,
        String contactEmail,
        LocalDateTime registrationDeadline,
        List<String> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long parentEventId,
        String recurrenceRule
) {
    public static EventDTO from(
            Event event,
            long attendingCount,
            Long availableSpots,
            long waitlistedCount,
            Long viewCount,
            Long interestedCount
    ) {
        return new EventDTO(
                event.id,
                event.title,
                event.description,
                event.location,
                event.startDate,
                event.endDate,
                event.category,
                event.faculty,
                event.bannerUrl,
                (event.creator != null ? event.creator.id : null),
                event.status,
                event.capacity,
                event.allDay,
                event.featured,
                event.featuredAt,
                attendingCount,
                availableSpots,
                waitlistedCount,
                viewCount,
                interestedCount,
                event.websiteUrl,
                event.contactEmail,
                event.registrationDeadline,
                event.tags != null ? List.copyOf(event.tags) : List.of(),
                event.createdAt,
                event.updatedAt,
                event.parentEventId,
                event.recurrenceRule
        );
    }
}
