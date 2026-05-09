package ch.unige.events.report.dto;

import ch.unige.events.report.entity.EventStub;
import ch.unige.events.report.entity.Report;
import ch.unige.events.report.entity.ReportReason;
import ch.unige.events.report.entity.ReportStatus;
import ch.unige.events.report.entity.UserStub;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mirror of legacy ReportDTO. The reporter displayName fallback chain
 * (displayName → "first last" → email) is preserved.
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
    public static ReportDTO from(Report r) {
        return new ReportDTO(
                r.id,
                eventId(r.event),
                eventTitle(r.event),
                reporterId(r.reporter),
                reporterDisplayName(r.reporter),
                r.reason,
                r.description,
                r.status,
                r.moderationNote,
                r.createdAt,
                r.reviewedAt,
                r.reviewedBy != null ? r.reviewedBy.id : null
        );
    }

    private static Long eventId(EventStub event) {
        return event != null ? event.id : null;
    }

    private static String eventTitle(EventStub event) {
        return event != null ? event.title : null;
    }

    private static UUID reporterId(UserStub reporter) {
        return reporter != null ? reporter.id : null;
    }

    private static String reporterDisplayName(UserStub reporter) {
        if (reporter == null) return null;
        if (isNotBlank(reporter.displayName)) return reporter.displayName;
        String first = reporter.firstName != null ? reporter.firstName : "";
        String last = reporter.lastName != null ? reporter.lastName : "";
        String combined = (first + " " + last).trim();
        if (!combined.isEmpty()) return combined;
        return reporter.email;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
