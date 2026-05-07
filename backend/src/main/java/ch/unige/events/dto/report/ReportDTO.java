package ch.unige.events.dto.report;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.Report;
import ch.unige.events.entity.ReportReason;
import ch.unige.events.entity.ReportStatus;
import ch.unige.events.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

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

    private static Long eventId(Event event) {
        return event != null ? event.id : null;
    }

    private static String eventTitle(Event event) {
        return event != null ? event.title : null;
    }

    private static UUID reporterId(User reporter) {
        return reporter != null ? reporter.id : null;
    }

    private static String reporterDisplayName(User reporter) {
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
