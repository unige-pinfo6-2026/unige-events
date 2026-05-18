package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.domain.dto.CommentVisibilityProjection;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        @Override public List<UUID> getAttendeeIds(Long eventId, String status) { throw new UnsupportedOperationException(); }
        @Override public CommentVisibilityProjection getCommentVisibility(Long commentId, UUID callerId) { throw new UnsupportedOperationException(); }
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

    @Test
    void getAttendeeIdsFallback_returnsEmptyList() {
        List<UUID> result = CLIENT.getAttendeeIdsFallback(42L, "ATTENDING");
        // Degraded notification fan-out path — empty list lets the
        // notification consumer's for-each loop skip cleanly.
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getCommentVisibilityFallback_throws503() {
        // SCRUM-144 — the report-comment flow is critical : silently returning
        // null would surface as a NullPointerException in moderation-service.
        // The fallback throws WebApplicationException(503) so the caller can
        // produce a meaningful error to the browser.
        UUID caller = UUID.randomUUID();
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> CLIENT.getCommentVisibilityFallback(42L, caller));
        assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(),
                ex.getResponse().getStatus());
    }
}
