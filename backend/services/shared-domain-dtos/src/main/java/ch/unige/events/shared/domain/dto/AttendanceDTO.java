package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.AttendanceStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cross-service projection of an Attendance row. engagement-service is
 * the only producer (via {@code GET /users/{id}/attendances} and the
 * internal {@code GET /events/{eventId}/attendance-summary} aggregator).
 * The {@code displayName} / {@code avatarUrl} fields are populated by
 * engagement-service via its own UserServiceClient before serializing.
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
}
