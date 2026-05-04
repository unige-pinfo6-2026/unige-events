package ch.unige.events.dto.report;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.Report;
import ch.unige.events.entity.ReportReason;
import ch.unige.events.entity.ReportStatus;
import ch.unige.events.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReportDTOTest {

    @Test
    void from_fullReport_projectsAllFields() {
        UUID reporterId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        LocalDateTime reviewedAt = LocalDateTime.now();

        User reporter = new User();
        reporter.id = reporterId;
        User reviewer = new User();
        reviewer.id = reviewerId;
        Event event = new Event();
        event.id = 42L;

        Report report = new Report();
        report.id = 7L;
        report.event = event;
        report.reporter = reporter;
        report.reason = ReportReason.SPAM;
        report.description = "Looks like spam";
        report.status = ReportStatus.REVIEWED;
        report.moderationNote = "Confirmed spam";
        report.createdAt = createdAt;
        report.reviewedAt = reviewedAt;
        report.reviewedBy = reviewer;

        ReportDTO dto = ReportDTO.from(report);

        assertEquals(7L, dto.id());
        assertEquals(42L, dto.eventId());
        assertEquals(reporterId, dto.reporterId());
        assertEquals(ReportReason.SPAM, dto.reason());
        assertEquals("Looks like spam", dto.description());
        assertEquals(ReportStatus.REVIEWED, dto.status());
        assertEquals("Confirmed spam", dto.moderationNote());
        assertEquals(createdAt, dto.createdAt());
        assertEquals(reviewedAt, dto.reviewedAt());
        assertEquals(reviewerId, dto.reviewedBy());
    }

    @Test
    void from_pendingReport_reviewedFieldsAreNull() {
        Event event = new Event();
        event.id = 1L;
        User reporter = new User();
        reporter.id = UUID.randomUUID();

        Report report = new Report();
        report.id = 1L;
        report.event = event;
        report.reporter = reporter;
        report.reason = ReportReason.OTHER;
        report.status = ReportStatus.PENDING;
        report.createdAt = LocalDateTime.now();
        // moderationNote, reviewedAt, reviewedBy left null

        ReportDTO dto = ReportDTO.from(report);

        assertEquals(ReportStatus.PENDING, dto.status());
        assertNull(dto.moderationNote());
        assertNull(dto.reviewedAt());
        assertNull(dto.reviewedBy());
    }

    @Test
    void from_nullReporter_reporterIdIsNull() {
        Event event = new Event();
        event.id = 1L;

        Report report = new Report();
        report.id = 1L;
        report.event = event;
        report.reporter = null; // soft-deleted reporter
        report.reason = ReportReason.FAKE;
        report.status = ReportStatus.PENDING;
        report.createdAt = LocalDateTime.now();

        ReportDTO dto = ReportDTO.from(report);

        assertNull(dto.reporterId());
        assertEquals(1L, dto.eventId());
    }

    @Test
    void from_nullReviewedBy_reviewedByIdIsNull() {
        Event event = new Event();
        event.id = 1L;

        Report report = new Report();
        report.id = 1L;
        report.event = event;
        report.reason = ReportReason.INAPPROPRIATE;
        report.status = ReportStatus.PENDING;
        report.createdAt = LocalDateTime.now();
        report.reviewedBy = null;

        ReportDTO dto = ReportDTO.from(report);

        assertNull(dto.reviewedBy());
    }
}
