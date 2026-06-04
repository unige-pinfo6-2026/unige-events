package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventCoOrganizerDTOTest {

    @Test
    void canonicalConstructor_acceptedStatus_keepsAllFields() {
        UUID userId = UUID.randomUUID();
        LocalDateTime invitedAt = LocalDateTime.of(2026, Month.MAY, 1, 12, 0);
        EventCoOrganizerDTO dto = new EventCoOrganizerDTO(
            7L, 42L, userId, "Alice", "https://avatars/alice.png",
            CoOrganizerStatus.ACCEPTED, invitedAt);

        assertEquals(7L, dto.id());
        assertEquals(42L, dto.eventId());
        assertEquals(userId, dto.userId());
        assertEquals("Alice", dto.displayName());
        assertEquals("https://avatars/alice.png", dto.avatarUrl());
        assertEquals(CoOrganizerStatus.ACCEPTED, dto.status());
        assertEquals(invitedAt, dto.invitedAt());
    }

    @Test
    void canonicalConstructor_pendingStatus_avatarMayBeNull() {
        EventCoOrganizerDTO dto = new EventCoOrganizerDTO(
            null, 99L, UUID.randomUUID(), "Bob", null,
            CoOrganizerStatus.PENDING, LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0));
        assertNull(dto.id());
        assertNull(dto.avatarUrl());
        assertEquals(CoOrganizerStatus.PENDING, dto.status());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID id = UUID.randomUUID();
        LocalDateTime t = LocalDateTime.of(2026, Month.MAY, 1, 12, 0);
        EventCoOrganizerDTO a = new EventCoOrganizerDTO(1L, 1L, id, "X", null, CoOrganizerStatus.ACCEPTED, t);
        EventCoOrganizerDTO b = new EventCoOrganizerDTO(1L, 1L, id, "X", null, CoOrganizerStatus.ACCEPTED, t);
        EventCoOrganizerDTO c = new EventCoOrganizerDTO(2L, 1L, id, "X", null, CoOrganizerStatus.ACCEPTED, t);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
