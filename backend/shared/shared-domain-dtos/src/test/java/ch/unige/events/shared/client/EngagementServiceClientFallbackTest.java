package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit test of the @Fallback default methods on EngagementServiceClient.
 * Same rationale as EventServiceClientFallbackTest.
 */
class EngagementServiceClientFallbackTest {

    private static final EngagementServiceClient CLIENT = new EngagementServiceClient() {
        @Override public AttendanceSummary getAttendanceSummary(long eventId) { throw new UnsupportedOperationException(); }
        @Override public List<AttendanceDTO> getUserAttendances(UUID id, String status) { throw new UnsupportedOperationException(); }
        @Override public Map<Long, AttendanceSummary> getAttendanceSummariesBulk(List<Long> ids) { throw new UnsupportedOperationException(); }
    };

    @Test
    void getAttendanceSummaryFallback_returnsZeroSummary() {
        AttendanceSummary summary = CLIENT.getAttendanceSummaryFallback(42L);
        assertNotNull(summary);
        assertEquals(0L, summary.attending());
        assertEquals(0L, summary.waitlisted());
    }

    @Test
    void getUserAttendancesFallback_returnsEmptyList() {
        List<AttendanceDTO> result = CLIENT.getUserAttendancesFallback(UUID.randomUUID(), "ATTENDING");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getAttendanceSummariesBulkFallback_returnsEmptyMap() {
        Map<Long, AttendanceSummary> result = CLIENT.getAttendanceSummariesBulkFallback(List.of(1L, 2L));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
