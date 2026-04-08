package ch.unige.events.dto.attendance;

import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AttendanceDTO(
        Long id,
        UUID userId,
        Long eventId,
        AttendanceStatus status,
        LocalDateTime createdAt
) {
    public static AttendanceDTO from(Attendance attendance) {
        return new AttendanceDTO(
                attendance.id,
                attendance.userId,
                attendance.eventId,
                attendance.status,
                attendance.createdAt
        );
    }
}
