package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.AttendanceStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttendanceDTOTest {

    @Test
    void recordExposesAllFields() {
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
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
}
