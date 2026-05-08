package ch.unige.events.dto.coorganizer;

import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.EventCoOrganizer;
import ch.unige.events.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record CoOrganizerDTO(
        Long id,
        UUID userId,
        String displayName,
        String avatarUrl,
        CoOrganizerStatus status,
        LocalDateTime invitedAt
) {
    public static CoOrganizerDTO from(EventCoOrganizer entity, User user) {
        return new CoOrganizerDTO(
                entity.id,
                entity.userId,
                user != null ? user.displayName : null,
                user != null ? user.avatarUrl : null,
                entity.status,
                entity.invitedAt
        );
    }
}
