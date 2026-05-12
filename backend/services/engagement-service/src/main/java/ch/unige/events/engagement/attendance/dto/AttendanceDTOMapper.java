package ch.unige.events.engagement.attendance.dto;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

/**
 * Mapper from {@link Attendance} JPA entity to {@link AttendanceDTO}
 * (shared record). Lives in engagement-service (not in
 * shared-domain-dtos) because it imports the JPA entity.
 *
 * <p>Décision A finalization-ultimate: replaces the service-local
 * AttendanceDTO record with the shared one consumed cross-service.
 */
public final class AttendanceDTOMapper {

    private AttendanceDTOMapper() {}

    /** Without enrichment (id-only payload, used cross-service). */
    public static AttendanceDTO from(Attendance attendance) {
        return new AttendanceDTO(
                attendance.id, attendance.userId, attendance.eventId,
                attendance.status, attendance.createdAt,
                null, null);
    }

    /** With user enrichment (display name + avatar from UserServiceClient). */
    public static AttendanceDTO from(Attendance attendance, UserPublicResponse user) {
        return new AttendanceDTO(
                attendance.id, attendance.userId, attendance.eventId,
                attendance.status, attendance.createdAt,
                user != null ? user.displayName() : null,
                user != null ? user.avatarUrl() : null);
    }
}
