package ch.unige.events.event.coorganizer.dto;

import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;

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
