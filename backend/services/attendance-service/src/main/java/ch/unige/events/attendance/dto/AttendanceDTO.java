package ch.unige.events.attendance.dto;

import ch.unige.events.attendance.entity.Attendance;
import ch.unige.events.attendance.entity.AttendanceStatus;
import ch.unige.events.attendance.entity.UserStub;

import java.time.LocalDateTime;
import java.util.UUID;

public record AttendanceDTO(
        Long id,
        UUID userId,
        Long eventId,
        AttendanceStatus status,
        LocalDateTime createdAt,
        String displayName,
        String avatarUrl
) {
    public static AttendanceDTO from(Attendance attendance, UserStub user) {
        return new AttendanceDTO(
                attendance.id,
                attendance.userId,
                attendance.eventId,
                attendance.status,
                attendance.createdAt,
                user != null ? user.displayName : null,
                user != null ? user.avatarUrl : null
        );
    }
}
