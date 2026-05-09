package ch.unige.events.event.favorite.dto;

import ch.unige.events.event.entity.EventCategory;
import ch.unige.events.event.entity.EventStatus;
import ch.unige.events.event.entity.Event;
import ch.unige.events.event.entity.Faculty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Mirror of the legacy monolith's EventDTO record. favorite-service emits
 * this shape on {@code GET /users/me/favorites} to keep the OpenAPI
 * contract invariant during the soft-extraction (cf. roadmap PR 3).
 *
 * <p>{@code viewCount} and {@code interestedCount} are nulled because
 * those metrics are owned by view-service / stats-service and aren't
 * fetched cross-service in S8 — the legacy monolith already passes
 * {@code null} for them on this endpoint, so the contract is preserved.
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
                event.creator != null ? event.creator.id : null,
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
