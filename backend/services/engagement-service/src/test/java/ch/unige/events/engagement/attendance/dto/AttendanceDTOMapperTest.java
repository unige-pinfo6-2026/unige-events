package ch.unige.events.engagement.attendance.dto;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttendanceDTOMapperTest {

    @Test
    void from_attendanceWithoutEnrichment_keepsAllIdFieldsButNullDisplayAndAvatar() {
        UUID userId = UUID.randomUUID();
        LocalDateTime created = LocalDateTime.of(2026, 5, 1, 12, 0);
        Attendance a = new Attendance();
        a.id = 7L;
        a.userId = userId;
        a.eventId = 42L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = created;

        AttendanceDTO dto = AttendanceDTOMapper.from(a);

        assertEquals(7L, dto.id());
        assertEquals(userId, dto.userId());
        assertEquals(42L, dto.eventId());
        assertEquals(AttendanceStatus.ATTENDING, dto.status());
        assertEquals(created, dto.createdAt());
        assertNull(dto.displayName());
        assertNull(dto.avatarUrl());
    }

    @Test
    void from_attendanceWithUser_enrichesDisplayNameAndAvatar() {
        UUID userId = UUID.randomUUID();
        Attendance a = new Attendance();
        a.id = 1L;
        a.userId = userId;
        a.eventId = 99L;
        a.status = AttendanceStatus.WAITLISTED;
        a.createdAt = LocalDateTime.now();

        UserPublicResponse user = new UserPublicResponse(
            userId, "Alice", null, null, null, null,
            "https://avatars/alice.png", null, 0L, 0L, null);

        AttendanceDTO dto = AttendanceDTOMapper.from(a, user);

        assertEquals("Alice", dto.displayName());
        assertEquals("https://avatars/alice.png", dto.avatarUrl());
        assertEquals(AttendanceStatus.WAITLISTED, dto.status());
    }

    @Test
    void from_attendanceWithNullUser_keepsDisplayAndAvatarNull() {
        Attendance a = new Attendance();
        a.id = 1L;
        a.userId = UUID.randomUUID();
        a.eventId = 99L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.now();

        AttendanceDTO dto = AttendanceDTOMapper.from(a, null);

        assertNull(dto.displayName());
        assertNull(dto.avatarUrl());
    }
}
