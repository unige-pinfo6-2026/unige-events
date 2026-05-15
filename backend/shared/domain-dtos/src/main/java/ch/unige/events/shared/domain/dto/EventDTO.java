package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)

/**
 * Cross-service projection of an Event row. event-service is the only
 * producer (via {@code GET /events/{id}} and {@code GET /events?ids=...}).
 *
 * <p>The {@code attendingCount}, {@code availableSpots}, {@code waitlistedCount},
 * {@code viewCount}, {@code interestedCount} fields are populated by event-service
 * when it has local access to the underlying tables (favorites/views post
 * consolidation 2.2.2/2.2.3). Cross-service consumers receive them as nullable
 * pre-computed snapshots.
 *
 * <p>The {@code coOrganizerOf} field is set when the consumer passes
 * {@code ?check-co-org-of=&lt;UUID&gt;} on the {@code GET /events/{id}}
 * endpoint — used by engagement-service / moderation-service to evaluate
 * the SCRUM-136 cascade without inlining the rule.
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
        Long attendingCount,
        Long availableSpots,
        Long waitlistedCount,
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
}
