package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.AttendanceStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)

/**
 * Cross-service projection of an Attendance row. engagement-service is
 * the only producer (via {@code GET /users/{id}/attendances} and the
 * internal {@code GET /events/{eventId}/attendance-summary} aggregator).
 * The {@code displayName} / {@code avatarUrl} / {@code username} fields are
 * populated by engagement-service via its own UserServiceClient before
 * serializing.
 *
 * <p>SCRUM-169 — {@code username} added to allow {@code AttendeeCard} on
 * the frontend to build {@code /profile/{username}} links without a
 * round-trip {@code GET /users/{id}}. {@code null} for orphan rows.
 */
public record AttendanceDTO(
        Long id,
        UUID userId,
        Long eventId,
        AttendanceStatus status,
        LocalDateTime createdAt,
        String displayName,
        String avatarUrl,
        String username
) {
    /**
     * Backward-compatible 7-arg constructor (pre-SCRUM-169) used by tests
     * that build mock payloads. Production code always populates
     * {@code username} via the engagement-service mapper.
     */
    public AttendanceDTO(
            Long id,
            UUID userId,
            Long eventId,
            AttendanceStatus status,
            LocalDateTime createdAt,
            String displayName,
            String avatarUrl
    ) {
        this(id, userId, eventId, status, createdAt, displayName, avatarUrl, null);
    }
}
