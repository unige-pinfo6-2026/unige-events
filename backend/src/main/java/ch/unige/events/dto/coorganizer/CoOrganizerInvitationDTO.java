package ch.unige.events.dto.coorganizer;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.EventCoOrganizer;

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
