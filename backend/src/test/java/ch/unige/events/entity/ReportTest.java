package ch.unige.events.entity;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ReportTest {

    @Test
    void defaultStatus_isPending() {
        Report report = new Report();
        assertEquals(ReportStatus.PENDING, report.status);
    }

    @Test
    void prePersist_setsCreatedAt() {
        Report report = new Report();
        assertNull(report.createdAt);

        report.prePersist();

        assertNotNull(report.createdAt);
    }

    @Test
    void fieldsAreAssignable() {
        Report report = new Report();
        report.reason = ReportReason.INAPPROPRIATE;
        report.description = "Inappropriate content in description";
        report.status = ReportStatus.REVIEWED;

        assertEquals(ReportReason.INAPPROPRIATE, report.reason);
        assertEquals("Inappropriate content in description", report.description);
        assertEquals(ReportStatus.REVIEWED, report.status);
    }

    @Test
    void newFieldsAreAssignable() {
        Report report = new Report();
        User reviewer = new User();
        LocalDateTime now = LocalDateTime.now();

        report.moderationNote = "Spam confirmé.";
        report.reviewedAt = now;
        report.reviewedBy = reviewer;

        assertEquals("Spam confirmé.", report.moderationNote);
        assertEquals(now, report.reviewedAt);
        assertSame(reviewer, report.reviewedBy);
    }
}
