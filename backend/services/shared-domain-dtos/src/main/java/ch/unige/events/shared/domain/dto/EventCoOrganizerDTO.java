package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.CoOrganizerStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cross-service projection of an EventCoOrganizer row. event-service is
 * the only producer (since the {@code event_co_organizers} table moved
 * into event-service in consolidation Étape 2.2.4).
 */
public record EventCoOrganizerDTO(
        Long id,
        Long eventId,
        UUID userId,
        String displayName,
        String avatarUrl,
        CoOrganizerStatus status,
        LocalDateTime invitedAt
) {
}
