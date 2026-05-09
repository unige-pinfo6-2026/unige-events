package ch.unige.events.report.dto;

import ch.unige.events.report.entity.Report;
import ch.unige.events.report.entity.ReportReason;
import ch.unige.events.report.entity.ReportStatus;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mirror of legacy ReportDTO. The reporter displayName fallback chain
 * (displayName → "first last" → email) is preserved through the
 * UserPublicResponse projection (which only carries displayName +
 * avatarUrl post-consolidation, so the "first last" / email fallbacks
 * become null when the cross-service projection has no display name).
 *
 * <p>Étape 3.2 finalization-ultimate (STUB-001 / Décision F): no more
 * UserStub / EventStub navigation — enrichment is fed in by the
 * service layer via REST clients to event-service and user-service.
 */
public record ReportDTO(
        Long id,
        Long eventId,
        String eventTitle,
        UUID reporterId,
        String reporterDisplayName,
        ReportReason reason,
        String description,
        ReportStatus status,
        String moderationNote,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        UUID reviewedBy
) {
    public static ReportDTO from(Report r, EventDTO event, UserPublicResponse reporter) {
        return new ReportDTO(
                r.id,
                r.eventId,
                event != null ? event.title() : null,
                r.reporterId,
                reporter != null ? reporter.displayName() : null,
                r.reason,
                r.description,
                r.status,
                r.moderationNote,
                r.createdAt,
                r.reviewedAt,
                r.reviewedById
        );
    }
}
