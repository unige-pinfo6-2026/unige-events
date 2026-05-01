package ch.unige.events.dto.event;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Faculty;

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
        long attendingCount,
        Long availableSpots,
        long waitlistedCount,
        String websiteUrl,
        String contactEmail,
        LocalDateTime registrationDeadline,
        List<String> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EventDTO from(
            Event event,
            long attendingCount,
            Long availableSpots,
            long waitlistedCount
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
                event.creator != null ? event.creator.id : null,
                event.status,
                event.capacity,
                event.allDay,
                event.featured,
                attendingCount,
                availableSpots,
                waitlistedCount,
                event.websiteUrl,
                event.contactEmail,
                event.registrationDeadline,
                event.tags != null ? List.copyOf(event.tags) : List.of(),
                event.createdAt,
                event.updatedAt
        );
    }
}
