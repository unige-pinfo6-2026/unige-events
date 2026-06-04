package ch.unige.events.report.entity;

import ch.unige.events.shared.domain.enums.ReportReason;
import ch.unige.events.shared.domain.enums.ReportStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lifecycle coverage for {@link Report} — the {@code @PrePersist} hook
 * stamps {@code createdAt} and the public fields are reachable.
 */
@QuarkusTest
class ReportTest {

    @Test
    void prePersist_setsCreatedAt() {
        Report r = new Report();
        r.eventId = 42L;
        r.reporterId = UUID.randomUUID();
        r.reason = ReportReason.SPAM;
        r.prePersist();
        assertNotNull(r.createdAt);
        assertTrue(r.createdAt.isAfter(LocalDateTime.of(2000, Month.JANUARY, 1, 0, 0).minusSeconds(2)));
    }

    @Test
    void publicFields_areAssignable() {
        UUID reporter = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        Report r = new Report();
        r.eventId = 99L;
        r.reporterId = reporter;
        r.reason = ReportReason.INAPPROPRIATE;
        r.description = "bad content";
        r.status = ReportStatus.REVIEWED;
        r.moderationNote = "removed";
        r.reviewedAt = LocalDateTime.of(2026, Month.MAY, 1, 12, 0);
        r.reviewedById = reviewer;
        r.createdAt = LocalDateTime.of(2026, Month.APRIL, 30, 10, 0);
        assertEquals(99L, r.eventId);
        assertEquals(reporter, r.reporterId);
        assertEquals(ReportReason.INAPPROPRIATE, r.reason);
        assertEquals("bad content", r.description);
        assertEquals(ReportStatus.REVIEWED, r.status);
        assertEquals("removed", r.moderationNote);
        assertEquals(reviewer, r.reviewedById);
    }

    @Test
    @TestTransaction
    void persist_andFindByQuery_works() {
        UUID reporter = UUID.randomUUID();
        Report r = new Report();
        r.eventId = 100_001L;
        r.reporterId = reporter;
        r.reason = ReportReason.SPAM;
        r.description = "spam";
        r.persist();

        long count = Report.count("reporterId = ?1 and eventId = ?2", reporter, 100_001L);
        assertEquals(1L, count);
    }
}
