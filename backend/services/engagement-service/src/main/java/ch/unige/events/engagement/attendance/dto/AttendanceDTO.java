package ch.unige.events.engagement.attendance.dto;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.engagement.attendance.entity.AttendanceStatus;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service-local AttendanceDTO. Étape 4.3 finalization-ultimate will
 * fold this into the shared {@code AttendanceDTO} via an
 * {@code AttendanceDTOMapper}.
 *
 * <p>Étape 3.1: enrichment now comes from a {@link UserPublicResponse}
 * fetched cross-service via {@code UserServiceClient.getById}, not from
 * the deleted local {@code UserStub} navigation.
 */
public record AttendanceDTO(
        Long id,
        UUID userId,
        Long eventId,
        AttendanceStatus status,
        LocalDateTime createdAt,
        String displayName,
        String avatarUrl
) {
    public static AttendanceDTO from(Attendance attendance, UserPublicResponse user) {
        return new AttendanceDTO(
                attendance.id,
                attendance.userId,
                attendance.eventId,
                attendance.status,
                attendance.createdAt,
                user != null ? user.displayName() : null,
                user != null ? user.avatarUrl() : null
        );
    }
}
