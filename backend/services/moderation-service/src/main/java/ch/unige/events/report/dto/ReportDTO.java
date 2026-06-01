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
 *
 * <p>QA bug batch (bug ③) — discriminateur explicite {@code targetType}
 * ("EVENT" | "COMMENT", dérivé de {@code commentId}) + {@code commentContent}
 * (le corps du commentaire signalé, peuplé uniquement côté comment-report par
 * {@code ReportService.listByStatus} via l'endpoint interne engagement
 * {@code GET /comments/_internal-by-ids}). Le frontend admin n'a plus à
 * inférer la cible à partir des id null — il lit {@code targetType} et affiche
 * soit {@code eventTitle}, soit {@code commentContent}.
 */
public record ReportDTO(
        Long id,
        String targetType,
        Long eventId,
        Long commentId,
        String eventTitle,
        String commentContent,
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
    /** Discriminator values exposed in {@link #targetType}. */
    public static final String TARGET_EVENT = "EVENT";
    public static final String TARGET_COMMENT = "COMMENT";

    public static ReportDTO from(Report r, EventDTO event, UserPublicResponse reporter, String commentContent) {
        return new ReportDTO(
                r.id,
                r.commentId != null ? TARGET_COMMENT : TARGET_EVENT,
                r.eventId,
                r.commentId,
                event != null ? event.title() : null,
                commentContent,
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

    /** Convenience overload for event-bound reports (no comment content). */
    public static ReportDTO from(Report r, EventDTO event, UserPublicResponse reporter) {
        return from(r, event, reporter, null);
    }
}
