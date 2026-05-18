package ch.unige.events.report.entity;

import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SCRUM-144 — sentinel pour la CHECK XOR et les UKs partielles sur reports.
 *
 * <p>Vérifie que la migration V4 a bien posé :
 * <ul>
 *   <li>{@code report_target_xor} CHECK ((event_id IS NULL) &lt;&gt; (comment_id IS NULL))
 *       — un report doit cibler exactement un event OU un comment, jamais
 *       les deux ni aucun.</li>
 *   <li>{@code uq_report_event_partial} UK partiel sur (reporter_id, event_id)
 *       WHERE event_id IS NOT NULL.</li>
 *   <li>{@code uq_report_comment_partial} UK partiel sur (reporter_id, comment_id)
 *       WHERE comment_id IS NOT NULL.</li>
 * </ul>
 */
@SuppressWarnings("java:S1612")
@QuarkusTest
class ReportEntityXorSentinelTest {

    @BeforeEach
    @AfterEach
    void truncate() {
        QuarkusTransaction.requiringNew().run(() -> Report.deleteAll());
    }

    private static Report newReport(UUID reporterId, Long eventId, Long commentId) {
        Report r = new Report();
        r.eventId = eventId;
        r.commentId = commentId;
        r.reporterId = reporterId;
        r.reason = ReportReason.SPAM;
        r.status = ReportStatus.PENDING;
        return r;
    }

    @Test
    void insert_eventOnly_succeeds() {
        UUID reporter = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> newReport(reporter, 42L, null).persist());
        assertEquals(1L, Report.count());
    }

    @Test
    void insert_commentOnly_succeeds() {
        UUID reporter = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> newReport(reporter, null, 99L).persist());
        assertEquals(1L, Report.count());
    }

    @Test
    void insert_bothNull_throwsConstraintViolation() {
        UUID reporter = UUID.randomUUID();
        assertThrows(Exception.class, () -> QuarkusTransaction.requiringNew()
                .run(() -> newReport(reporter, null, null).persist()));
        assertEquals(0L, Report.count());
    }

    @Test
    void insert_bothNonNull_throwsConstraintViolation() {
        UUID reporter = UUID.randomUUID();
        assertThrows(Exception.class, () -> QuarkusTransaction.requiringNew()
                .run(() -> newReport(reporter, 42L, 99L).persist()));
        assertEquals(0L, Report.count());
    }

    @Test
    @Disabled("""
            Partial unique indexes (`CREATE UNIQUE INDEX ... WHERE event_id IS NOT NULL`)
            are PG 9.5+ SQL — JPA `@UniqueConstraint` has no equivalent and Hibernate
            does not generate them via annotations either. The constraints live only
            in the Flyway migration `V4__add_comment_id_to_reports.sql`, and Flyway
            is disabled in %test (drop-and-create from JPA annotations). The
            constraint is validated end-to-end by the CI integration job + by the
            isUniqueReportCommentConflict helper unit tests in
            ReportServiceCreateForCommentTest.""")
    void uniqueIndex_eventPartial_blocksDuplicateReporterEvent() {
        UUID reporter = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> newReport(reporter, 42L, null).persist());
        assertThrows(Exception.class, () -> QuarkusTransaction.requiringNew()
                .run(() -> newReport(reporter, 42L, null).persist()));
        assertEquals(1L, Report.count());
    }

    @Test
    @Disabled("""
            Partial unique index, same rationale as uniqueIndex_eventPartial — see
            the comment on the sibling test. Coverage is provided by the
            isUniqueReportCommentConflict helper assertions in
            ReportServiceCreateForCommentTest.""")
    void uniqueIndex_commentPartial_blocksDuplicateReporterComment() {
        UUID reporter = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> newReport(reporter, null, 99L).persist());
        assertThrows(Exception.class, () -> QuarkusTransaction.requiringNew()
                .run(() -> newReport(reporter, null, 99L).persist()));
        assertEquals(1L, Report.count());
    }

    @Test
    void uniqueIndex_allowsSameReporterDifferentTargets() {
        UUID reporter = UUID.randomUUID();
        // Same reporter, but one targets an event and the other a comment —
        // the partial UKs are independent (event UK doesn't see the comment
        // row, comment UK doesn't see the event row).
        assertDoesNotThrow(() -> {
            QuarkusTransaction.requiringNew().run(() -> newReport(reporter, 42L, null).persist());
            QuarkusTransaction.requiringNew().run(() -> newReport(reporter, null, 99L).persist());
        });
        assertEquals(2L, Report.count());
    }

    @Test
    void uniqueIndex_allowsDifferentRepartersSameTarget() {
        // Two distinct reporters can each report the same event.
        assertDoesNotThrow(() -> {
            QuarkusTransaction.requiringNew().run(() -> newReport(UUID.randomUUID(), 42L, null).persist());
            QuarkusTransaction.requiringNew().run(() -> newReport(UUID.randomUUID(), 42L, null).persist());
        });
        assertEquals(2L, Report.count());
    }
}
