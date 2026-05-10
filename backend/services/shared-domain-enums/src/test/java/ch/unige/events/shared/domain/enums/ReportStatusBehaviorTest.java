package ch.unige.events.shared.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the behavioral helpers added to {@link ReportStatus} in
 * Étape 24.6.3 (Étape 24.7.7 — C9 coverage).
 */
class ReportStatusBehaviorTest {

    @Test
    void isClosed_trueForReviewedAndDismissed_falseForPending() {
        assertFalse(ReportStatus.PENDING.isClosed());
        assertTrue(ReportStatus.REVIEWED.isClosed());
        assertTrue(ReportStatus.DISMISSED.isClosed());
    }

    @Test
    void isOpen_onlyPending() {
        assertTrue(ReportStatus.PENDING.isOpen());
        assertFalse(ReportStatus.REVIEWED.isOpen());
        assertFalse(ReportStatus.DISMISSED.isOpen());
    }

    @Test
    void closedAndOpen_arePartition() {
        // For every value, exactly one of isOpen()/isClosed() is true.
        for (ReportStatus s : ReportStatus.values()) {
            assertTrue(s.isOpen() ^ s.isClosed(),
                    "isOpen/isClosed must partition the value space, failed on " + s);
        }
    }
}
