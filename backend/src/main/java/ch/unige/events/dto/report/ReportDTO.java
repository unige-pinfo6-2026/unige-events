package ch.unige.events.dto.report;

import ch.unige.events.entity.Report;
import ch.unige.events.entity.ReportReason;
import ch.unige.events.entity.ReportStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReportDTO(
        Long id,
        Long eventId,
        UUID reporterId,
        ReportReason reason,
        String description,
        ReportStatus status,
        String moderationNote,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        UUID reviewedBy
) {
    public static ReportDTO from(Report r) {
        return new ReportDTO(
                r.id,
                r.event != null ? r.event.id : null,
                r.reporter != null ? r.reporter.id : null,
                r.reason,
                r.description,
                r.status,
                r.moderationNote,
                r.createdAt,
                r.reviewedAt,
                r.reviewedBy != null ? r.reviewedBy.id : null
        );
    }
}
