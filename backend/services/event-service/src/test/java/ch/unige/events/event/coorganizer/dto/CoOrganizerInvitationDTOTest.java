package ch.unige.events.event.coorganizer.dto;

import ch.unige.events.event.coorganizer.entity.EventCoOrganizer;
import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@SuppressWarnings("java:S100")
class CoOrganizerInvitationDTOTest {

    @Test
    void canonicalConstructor_keepsAllFields() {
        EventDTO event = sampleEvent(1L);
        LocalDateTime invitedAt = LocalDateTime.of(2026, 5, 1, 12, 0);
        CoOrganizerInvitationDTO dto = new CoOrganizerInvitationDTO(
                3L, event, CoOrganizerStatus.PENDING, invitedAt);
        assertEquals(3L, dto.id());
        assertSame(event, dto.event());
        assertEquals(CoOrganizerStatus.PENDING, dto.status());
        assertEquals(invitedAt, dto.invitedAt());
    }

    @Test
    void from_buildsFromEntityAndEvent() {
        EventCoOrganizer entity = new EventCoOrganizer();
        entity.id = 99L;
        entity.userId = UUID.randomUUID();
        entity.status = CoOrganizerStatus.ACCEPTED;
        entity.invitedAt = LocalDateTime.of(2026, 5, 2, 10, 0);
        EventDTO event = sampleEvent(7L);

        CoOrganizerInvitationDTO dto = CoOrganizerInvitationDTO.from(entity, event);

        assertEquals(99L, dto.id());
        assertSame(event, dto.event());
        assertEquals(CoOrganizerStatus.ACCEPTED, dto.status());
        assertEquals(entity.invitedAt, dto.invitedAt());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        EventDTO event = sampleEvent(1L);
        LocalDateTime t = LocalDateTime.of(2026, 5, 1, 12, 0);
        CoOrganizerInvitationDTO a = new CoOrganizerInvitationDTO(1L, event, CoOrganizerStatus.PENDING, t);
        CoOrganizerInvitationDTO b = new CoOrganizerInvitationDTO(1L, event, CoOrganizerStatus.PENDING, t);
        CoOrganizerInvitationDTO c = new CoOrganizerInvitationDTO(1L, event, CoOrganizerStatus.ACCEPTED, t);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    private static EventDTO sampleEvent(Long id) {
        LocalDateTime t = LocalDateTime.of(2026, 6, 1, 10, 0);
        return new EventDTO(id, "Title", null, "L", t, t, EventCategory.OTHER, null, null,
                UUID.randomUUID(), EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, null, null, null, null, null, List.of(), t, t, null, null, null);
    }
}
