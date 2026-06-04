package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.AttendanceStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttendanceDTOTest {

    @Test
    void recordExposesAllFields() {
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0);
        AttendanceDTO dto = new AttendanceDTO(7L, userId, 42L, AttendanceStatus.ATTENDING, now, "Alice", "a.png");
        assertEquals(7L, dto.id());
        assertEquals(userId, dto.userId());
        assertEquals(AttendanceStatus.ATTENDING, dto.status());
        assertEquals("Alice", dto.displayName());
    }

    @Test
    void waitlistedRecordKeepsNullEnrichment() {
        AttendanceDTO dto = new AttendanceDTO(1L, UUID.randomUUID(), 1L, AttendanceStatus.WAITLISTED, null, null, null);
        assertEquals(AttendanceStatus.WAITLISTED, dto.status());
        assertNull(dto.displayName());
    }

    @Test
    void canonicalConstructor_populatesUsername() {
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0);
        AttendanceDTO dto = new AttendanceDTO(9L, userId, 3L, AttendanceStatus.ATTENDING, now, "Bob", "b.png", "bob.smith");
        assertEquals(9L, dto.id());
        assertEquals(userId, dto.userId());
        assertEquals(3L, dto.eventId());
        assertEquals(now, dto.createdAt());
        assertEquals("Bob", dto.displayName());
        assertEquals("b.png", dto.avatarUrl());
        assertEquals("bob.smith", dto.username());
    }
}
