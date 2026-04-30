package ch.unige.events.entity;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

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
        report.reason = "Inappropriate content";
        report.status = ReportStatus.REVIEWED;

        assertEquals("Inappropriate content", report.reason);
        assertEquals(ReportStatus.REVIEWED, report.status);
    }
}
