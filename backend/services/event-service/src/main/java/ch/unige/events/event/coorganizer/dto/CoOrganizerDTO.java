package ch.unige.events.event.coorganizer.dto;

import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Étape 3.4 finalization-ultimate (STUB-001 / Décision F): factory now
 * takes a {@link UserPublicResponse} (cross-service projection) instead
 * of the deleted local {@code UserStub}.
 */
public record CoOrganizerDTO(
        Long id,
        UUID userId,
        String displayName,
        String avatarUrl,
        CoOrganizerStatus status,
        LocalDateTime invitedAt
) {
    public static CoOrganizerDTO from(EventCoOrganizer entity, UserPublicResponse user) {
        return new CoOrganizerDTO(
                entity.id,
                entity.userId,
                user != null ? user.displayName() : null,
                user != null ? user.avatarUrl() : null,
                entity.status,
                entity.invitedAt
        );
    }
}
