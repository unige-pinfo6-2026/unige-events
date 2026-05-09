package ch.unige.events.event.coorganizer.dto;

import ch.unige.events.event.entity.CoOrganizerStatus;
import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.event.entity.UserStub;

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
    public static CoOrganizerDTO from(EventCoOrganizer entity, UserStub user) {
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
