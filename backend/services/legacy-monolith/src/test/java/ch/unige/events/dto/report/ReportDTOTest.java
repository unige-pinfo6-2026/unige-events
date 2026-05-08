package ch.unige.events.dto.report;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.Report;
import ch.unige.events.entity.ReportReason;
import ch.unige.events.entity.ReportStatus;
import ch.unige.events.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportDTOTest {

    @Test
    void from_fullReport_projectsAllFields() {
        UUID reporterId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        LocalDateTime reviewedAt = LocalDateTime.now();

        User reporter = new User();
        reporter.id = reporterId;
        reporter.displayName = "Alice Reporter";
        User reviewer = new User();
        reviewer.id = reviewerId;
        Event event = new Event();
        event.id = 42L;
        event.title = "Soirée d'intégration";

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
        assertEquals("Soirée d'intégration", dto.eventTitle());
        assertEquals(reporterId, dto.reporterId());
        assertEquals("Alice Reporter", dto.reporterDisplayName());
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
        event.title = "Conf IA";
        User reporter = new User();
        reporter.id = UUID.randomUUID();
        reporter.displayName = "Bob";

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
    void from_nullReporter_reporterFieldsAreNull() {
        Event event = new Event();
        event.id = 1L;
        event.title = "Event sans reporter";

        Report report = new Report();
        report.id = 1L;
        report.event = event;
        report.reporter = null; // soft-deleted reporter
        report.reason = ReportReason.FAKE;
        report.status = ReportStatus.PENDING;
        report.createdAt = LocalDateTime.now();

        ReportDTO dto = ReportDTO.from(report);

        assertNull(dto.reporterId());
        assertNull(dto.reporterDisplayName());
        assertEquals(1L, dto.eventId());
        assertEquals("Event sans reporter", dto.eventTitle());
    }

    @Test
    void from_nullEvent_eventFieldsAreNull() {
        User reporter = new User();
        reporter.id = UUID.randomUUID();
        reporter.displayName = "Carol";

        Report report = new Report();
        report.id = 1L;
        report.event = null; // theoretically impossible (NOT NULL FK) but defensive
        report.reporter = reporter;
        report.reason = ReportReason.INAPPROPRIATE;
        report.status = ReportStatus.PENDING;
        report.createdAt = LocalDateTime.now();

        ReportDTO dto = ReportDTO.from(report);

        assertNull(dto.eventId());
        assertNull(dto.eventTitle());
    }

    @Test
    void from_nullReviewedBy_reviewedByIdIsNull() {
        Event event = new Event();
        event.id = 1L;
        event.title = "Event";

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

    @Test
    void reporterDisplayName_fallbackToFirstLastName_whenDisplayNameBlank() {
        Event event = new Event();
        event.id = 1L;
        event.title = "Event";
        User reporter = new User();
        reporter.id = UUID.randomUUID();
        reporter.displayName = "  ";
        reporter.firstName = "Dani";
        reporter.lastName = "Doe";

        Report report = new Report();
        report.id = 1L;
        report.event = event;
        report.reporter = reporter;
        report.reason = ReportReason.SPAM;
        report.status = ReportStatus.PENDING;
        report.createdAt = LocalDateTime.now();

        ReportDTO dto = ReportDTO.from(report);

        assertEquals("Dani Doe", dto.reporterDisplayName());
    }

    @Test
    void reporterDisplayName_fallbackToEmail_whenAllNamesMissing() {
        Event event = new Event();
        event.id = 1L;
        event.title = "Event";
        User reporter = new User();
        reporter.id = UUID.randomUUID();
        reporter.email = "ghost@example.com";

        Report report = new Report();
        report.id = 1L;
        report.event = event;
        report.reporter = reporter;
        report.reason = ReportReason.OTHER;
        report.status = ReportStatus.PENDING;
        report.createdAt = LocalDateTime.now();

        ReportDTO dto = ReportDTO.from(report);

        assertEquals("ghost@example.com", dto.reporterDisplayName());
    }

    @Test
    void reporterDisplayName_partialName_returnsTrimmedValue() {
        Event event = new Event();
        event.id = 1L;
        event.title = "Event";
        User reporter = new User();
        reporter.id = UUID.randomUUID();
        reporter.firstName = "Eve";
        reporter.lastName = null;

        Report report = new Report();
        report.id = 1L;
        report.event = event;
        report.reporter = reporter;
        report.reason = ReportReason.OTHER;
        report.status = ReportStatus.PENDING;
        report.createdAt = LocalDateTime.now();

        ReportDTO dto = ReportDTO.from(report);

        assertEquals("Eve", dto.reporterDisplayName());
    }

    // ── Record contract (Sonar new-code coverage gate) ─────────────────────
    // Java records auto-generate equals/hashCode/toString with one branch per
    // field, which JaCoCo counts toward conditional coverage. The from() tests
    // above only exercise the projection, not the equality, so without these
    // assertions the file lands at ~68% on Sonar new-code (under the 80%
    // threshold). Two instances with all 12 fields populated identically and
    // a few mutations are enough to cover the full equals/hashCode chain.

    private static ReportDTO sampleDTO(UUID reporterId, UUID reviewerId, LocalDateTime ts) {
        return new ReportDTO(
                7L,
                42L,
                "Soirée d'intégration",
                reporterId,
                "Alice Reporter",
                ReportReason.SPAM,
                "Looks like spam",
                ReportStatus.REVIEWED,
                "Confirmed spam",
                ts,
                ts,
                reviewerId);
    }

    @Test
    void equals_andHashCode_followRecordContract() {
        UUID reporterId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        LocalDateTime ts = LocalDateTime.of(2026, 5, 7, 10, 0);

        ReportDTO a = sampleDTO(reporterId, reviewerId, ts);
        ReportDTO b = sampleDTO(reporterId, reviewerId, ts);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        // Reflexive + null + foreign type
        assertEquals(a, a);
        assertNotEquals(null, a);
        assertNotEquals("not a ReportDTO", a);
    }

    @Test
    void equals_distinguishesEachField() {
        UUID reporterId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        LocalDateTime ts = LocalDateTime.of(2026, 5, 7, 10, 0);
        ReportDTO base = sampleDTO(reporterId, reviewerId, ts);

        // Each mutation must produce a non-equal instance — exercises every
        // field's branch in the auto-generated equals().
        assertNotEquals(base, new ReportDTO(99L, 42L, "Soirée d'intégration", reporterId, "Alice Reporter",
                ReportReason.SPAM, "Looks like spam", ReportStatus.REVIEWED, "Confirmed spam", ts, ts, reviewerId));
        assertNotEquals(base, new ReportDTO(7L, 99L, "Soirée d'intégration", reporterId, "Alice Reporter",
                ReportReason.SPAM, "Looks like spam", ReportStatus.REVIEWED, "Confirmed spam", ts, ts, reviewerId));
        assertNotEquals(base, new ReportDTO(7L, 42L, "Other", reporterId, "Alice Reporter",
                ReportReason.SPAM, "Looks like spam", ReportStatus.REVIEWED, "Confirmed spam", ts, ts, reviewerId));
        assertNotEquals(base, new ReportDTO(7L, 42L, "Soirée d'intégration", UUID.randomUUID(), "Alice Reporter",
                ReportReason.SPAM, "Looks like spam", ReportStatus.REVIEWED, "Confirmed spam", ts, ts, reviewerId));
        assertNotEquals(base, new ReportDTO(7L, 42L, "Soirée d'intégration", reporterId, "Bob Other",
                ReportReason.SPAM, "Looks like spam", ReportStatus.REVIEWED, "Confirmed spam", ts, ts, reviewerId));
        assertNotEquals(base, new ReportDTO(7L, 42L, "Soirée d'intégration", reporterId, "Alice Reporter",
                ReportReason.OTHER, "Looks like spam", ReportStatus.REVIEWED, "Confirmed spam", ts, ts, reviewerId));
        assertNotEquals(base, new ReportDTO(7L, 42L, "Soirée d'intégration", reporterId, "Alice Reporter",
                ReportReason.SPAM, "Different", ReportStatus.REVIEWED, "Confirmed spam", ts, ts, reviewerId));
        assertNotEquals(base, new ReportDTO(7L, 42L, "Soirée d'intégration", reporterId, "Alice Reporter",
                ReportReason.SPAM, "Looks like spam", ReportStatus.DISMISSED, "Confirmed spam", ts, ts, reviewerId));
        assertNotEquals(base, new ReportDTO(7L, 42L, "Soirée d'intégration", reporterId, "Alice Reporter",
                ReportReason.SPAM, "Looks like spam", ReportStatus.REVIEWED, "Different note", ts, ts, reviewerId));
        assertNotEquals(base, new ReportDTO(7L, 42L, "Soirée d'intégration", reporterId, "Alice Reporter",
                ReportReason.SPAM, "Looks like spam", ReportStatus.REVIEWED, "Confirmed spam", ts.plusDays(1), ts, reviewerId));
        assertNotEquals(base, new ReportDTO(7L, 42L, "Soirée d'intégration", reporterId, "Alice Reporter",
                ReportReason.SPAM, "Looks like spam", ReportStatus.REVIEWED, "Confirmed spam", ts, ts.plusDays(1), reviewerId));
        assertNotEquals(base, new ReportDTO(7L, 42L, "Soirée d'intégration", reporterId, "Alice Reporter",
                ReportReason.SPAM, "Looks like spam", ReportStatus.REVIEWED, "Confirmed spam", ts, ts, UUID.randomUUID()));
    }

    @Test
    void toString_containsClassNameAndKeyFields() {
        ReportDTO dto = sampleDTO(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.of(2026, 5, 7, 10, 0));
        String s = dto.toString();
        assertNotNull(s);
        assertTrue(s.contains("ReportDTO"));
        assertTrue(s.contains("SPAM"));
    }
}
