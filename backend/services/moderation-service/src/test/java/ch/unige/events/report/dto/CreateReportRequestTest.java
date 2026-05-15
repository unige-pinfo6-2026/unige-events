package ch.unige.events.report.dto;

import ch.unige.events.shared.domain.enums.ReportReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CreateReportRequestTest {

    @Test
    void canonicalConstructor_keepsReasonAndDescription() {
        CreateReportRequest req = new CreateReportRequest(
                ReportReason.SPAM, "Posting the same link again and again");

        assertEquals(ReportReason.SPAM, req.reason());
        assertEquals("Posting the same link again and again", req.description());
    }

    @Test
    void canonicalConstructor_nullDescription_isAccepted() {
        CreateReportRequest req = new CreateReportRequest(ReportReason.OTHER, null);
        assertEquals(ReportReason.OTHER, req.reason());
        assertNull(req.description());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        CreateReportRequest a = new CreateReportRequest(ReportReason.FAKE, "fake event");
        CreateReportRequest b = new CreateReportRequest(ReportReason.FAKE, "fake event");
        CreateReportRequest c = new CreateReportRequest(ReportReason.INAPPROPRIATE, "fake event");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
