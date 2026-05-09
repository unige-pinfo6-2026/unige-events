package ch.unige.events.report.dto;

import ch.unige.events.shared.domain.enums.ReportStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HandleReportRequestTest {

    @Test
    void canonicalConstructor_reviewedStatus_keepsAllFields() {
        HandleReportRequest req = new HandleReportRequest(
                ReportStatus.REVIEWED, "content removed");

        assertEquals(ReportStatus.REVIEWED, req.status());
        assertEquals("content removed", req.moderationNote());
    }

    @Test
    void canonicalConstructor_dismissedStatus_nullNoteIsAccepted() {
        HandleReportRequest req = new HandleReportRequest(ReportStatus.DISMISSED, null);
        assertEquals(ReportStatus.DISMISSED, req.status());
        assertNull(req.moderationNote());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        HandleReportRequest a = new HandleReportRequest(ReportStatus.REVIEWED, "ok");
        HandleReportRequest b = new HandleReportRequest(ReportStatus.REVIEWED, "ok");
        HandleReportRequest c = new HandleReportRequest(ReportStatus.DISMISSED, "ok");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
