package ch.unige.events.event.coorganizer.dto;

import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Factory takes a {@link UserPublicResponse} (cross-service projection)
 * instead of the deleted local {@code UserStub}.
 *
 * <p>SCRUM-169 — {@code username} added (nullable) so the frontend
 * {@code EventOrganizerTeam} can build {@code /profile/{username}} links
 * for each row without an N+1 lookup against user-service.
 */
public record CoOrganizerDTO(
        Long id,
        UUID userId,
        String displayName,
        String avatarUrl,
        CoOrganizerStatus status,
        LocalDateTime invitedAt,
        String username
) {
    /** Backward-compatible 6-arg constructor (pre-SCRUM-169) — kept for tests. */
    public CoOrganizerDTO(
            Long id,
            UUID userId,
            String displayName,
            String avatarUrl,
            CoOrganizerStatus status,
            LocalDateTime invitedAt
    ) {
        this(id, userId, displayName, avatarUrl, status, invitedAt, null);
    }

    public static CoOrganizerDTO from(EventCoOrganizer entity, UserPublicResponse user) {
        return new CoOrganizerDTO(
                entity.id,
                entity.userId,
                user != null ? user.displayName() : null,
                user != null ? user.avatarUrl() : null,
                entity.status,
                entity.invitedAt,
                user != null ? user.username() : null
        );
    }
}
