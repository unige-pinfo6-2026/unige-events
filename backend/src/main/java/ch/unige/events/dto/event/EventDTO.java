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
        long interestedCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EventDTO from(Event event, long attendingCount, long interestedCount) {
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
                interestedCount,
                event.createdAt,
                event.updatedAt
        );
    }

    /**
     * @deprecated Zero-count convenience overload — must NOT be used in any API response path,
     *             as it always returns attendingCount=0 and interestedCount=0.
     *             Use {@link #from(Event, long, long)} with real bulk counts instead.
     *             Kept only for test fixtures that do not care about counts.
     */
    @Deprecated(forRemoval = false)
    public static EventDTO from(Event event) {
        return from(event, 0L, 0L);
    }
}
