package ch.unige.events.dto.coorganizer;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventCoOrganizer;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CoOrganizerDTOTest {

    @Test
    void from_withUser_projectsAllFields() {
        EventCoOrganizer entity = new EventCoOrganizer();
        entity.id = 42L;
        entity.eventId = 7L;
        entity.userId = UUID.randomUUID();
        entity.status = CoOrganizerStatus.PENDING;
        entity.invitedAt = LocalDateTime.of(2026, 4, 29, 10, 30);

        User user = new User();
        user.id = entity.userId;
        user.displayName = "Alice";
        user.avatarUrl = "https://cdn/alice.png";

        CoOrganizerDTO dto = CoOrganizerDTO.from(entity, user);

        assertEquals(42L, dto.id());
        assertEquals(entity.userId, dto.userId());
        assertEquals("Alice", dto.displayName());
        assertEquals("https://cdn/alice.png", dto.avatarUrl());
        assertEquals(CoOrganizerStatus.PENDING, dto.status());
        assertEquals(LocalDateTime.of(2026, 4, 29, 10, 30), dto.invitedAt());
    }

    @Test
    void from_withNullUser_setsDisplayNameAndAvatarToNull() {
        EventCoOrganizer entity = new EventCoOrganizer();
        entity.id = 1L;
        entity.eventId = 1L;
        entity.userId = UUID.randomUUID();
        entity.status = CoOrganizerStatus.ACCEPTED;
        entity.invitedAt = LocalDateTime.now();

        CoOrganizerDTO dto = CoOrganizerDTO.from(entity, null);

        assertNotNull(dto);
        assertNull(dto.displayName());
        assertNull(dto.avatarUrl());
        assertEquals(CoOrganizerStatus.ACCEPTED, dto.status());
    }

    @Test
    void from_invitation_includesEvent() {
        EventCoOrganizer entity = new EventCoOrganizer();
        entity.id = 99L;
        entity.eventId = 7L;
        entity.userId = UUID.randomUUID();
        entity.status = CoOrganizerStatus.PENDING;
        entity.invitedAt = LocalDateTime.now();

        EventDTO event = new EventDTO(
                7L, "My event", null, "Uni Mail",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                EventCategory.ACADEMIC, null, null,
                UUID.randomUUID(), EventStatus.PUBLISHED, null, false,
                0L, null, 0L,
                null, null, null, List.of(),
                LocalDateTime.now(), LocalDateTime.now()
        );

        CoOrganizerInvitationDTO dto = CoOrganizerInvitationDTO.from(entity, event);

        assertEquals(99L, dto.id());
        assertNotNull(dto.event());
        assertEquals("My event", dto.event().title());
        assertEquals(CoOrganizerStatus.PENDING, dto.status());
    }
}
