package ch.unige.events.event.coorganizer.dto;

import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.enums.Faculty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * EventDTO emitted by event-service for the {@code coorganizer} sub-domain.
 *
 * <p>FR — Variant intentionnellement dupliqué post-Décision E pour découpler
 * les contrats sub-domain. Ce variant porte actuellement les MÊMES types que
 * les autres variants — la séparation est un point de variation contrôlé prêt
 * à diverger sans casser les siblings, pas une variation déjà exprimée. NE
 * PAS consolider sans revisiter la spec.
 *
 * <p>EN — Variant intentionally duplicated per Decision E to decouple
 * sub-domain contracts. Currently carries the SAME types as the other
 * variants — the split is a controlled point of variation ready to diverge
 * without breaking siblings, not an already-expressed variation. DO NOT
 * consolidate without revisiting the spec.
 *
 * <p>Note: {@code coOrganizerOf} added for cross-variant uniformity — the
 * SCRUM-136 cascade flag is now available on every variant.
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
        String recurrenceRule,
        Boolean coOrganizerOf
) {
    public static EventDTO from(
            Event event,
            long attendingCount,
            Long availableSpots,
            long waitlistedCount,
            Long viewCount,
            Long interestedCount
    ) {
        return from(event, attendingCount, availableSpots, waitlistedCount, viewCount, interestedCount, null);
    }

    public static EventDTO from(
            Event event,
            long attendingCount,
            Long availableSpots,
            long waitlistedCount,
            Long viewCount,
            Long interestedCount,
            Boolean coOrganizerOf
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
                event.recurrenceRule,
                coOrganizerOf
        );
    }
}
