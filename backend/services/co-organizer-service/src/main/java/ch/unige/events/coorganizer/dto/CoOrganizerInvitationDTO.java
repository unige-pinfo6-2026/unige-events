package ch.unige.events.coorganizer.dto;

import ch.unige.events.coorganizer.entity.CoOrganizerStatus;
import ch.unige.events.coorganizer.entity.EventCoOrganizer;

import java.time.LocalDateTime;

public record CoOrganizerInvitationDTO(
        Long id,
        EventDTO event,
        CoOrganizerStatus status,
        LocalDateTime invitedAt
) {
    public static CoOrganizerInvitationDTO from(EventCoOrganizer entity, EventDTO event) {
        return new CoOrganizerInvitationDTO(entity.id, event, entity.status, entity.invitedAt);
    }
}
