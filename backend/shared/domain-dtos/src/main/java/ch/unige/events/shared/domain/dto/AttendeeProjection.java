package ch.unige.events.shared.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Minimal user projection used cross-service to feed the attendees-list
 * privacy filter (SCRUM-S7 — public participants list on event detail).
 *
 * <p>Carries {@code profilePublic} so the consumer
 * (engagement-service {@code AttendanceService.getAttendees}) can decide
 * whether to expose {@code displayName} / {@code avatarUrl} / {@code userId}
 * to non-organizer callers — without leaking the privacy flag back to the
 * frontend.
 *
 * <p>Producer: user-service via the internal endpoint
 * {@code GET /users/_internal-attendee-projections?ids=...}
 * (cf. {@code backend/docs/internal-endpoints.md}, entry #7).
 * <strong>Not</strong> exposed in {@code openapi.yaml} — internal-only DTO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AttendeeProjection(
        UUID id,
        String displayName,
        String avatarUrl,
        boolean profilePublic
) {}
