package ch.unige.events.report.dto;

import ch.unige.events.report.entity.Report;
import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;
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
 * <p>no more UserStub / EventStub navigation — enrichment is fed in by the
 * service layer via REST clients to event-service and user-service.
 *
 * <p>SCRUM-144 — {@code eventId} devient nullable et un nouveau champ
 * {@code commentId} (nullable) est exposé. Un report cible exactement
 * l'un des deux (XOR enforced en DB via {@code report_target_xor}).
 * Le {@code eventTitle} reste utilisable pour les reports event-bound ;
 * le frontend admin distingue les deux cas via les champs id non-null.
 */
public record ReportDTO(
        Long id,
        Long eventId,
        Long commentId,
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
                r.commentId,
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
