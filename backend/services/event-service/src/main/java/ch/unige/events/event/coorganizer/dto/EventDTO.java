package ch.unige.events.event.coorganizer.dto;

import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.enums.Faculty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Consumer-shape projection of {@link Event} for the {@code coorganizer}
 * sub-domain. Intentionally co-exists with sibling {@code EventDTO}
 * records in {@code event.dto} (master, carries {@code coOrganizerOf}),
 * {@code event.me.dto}, {@code event.favorite.dto} — each variant
 * differs by nullability of count fields.
 *
 * <p>Décision E finalization-complete: consolidation was attempted and
 * reverted to avoid regressing typing / coverage. DO NOT consolidate
 * without revisiting the spec.
 */
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
                event.creatorId,
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
