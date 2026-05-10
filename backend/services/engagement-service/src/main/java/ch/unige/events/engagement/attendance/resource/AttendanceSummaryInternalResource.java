package ch.unige.events.engagement.attendance.resource;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import ch.unige.events.shared.jaxrs.Internal;

import jakarta.annotation.security.PermitAll;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal endpoints rooted on {@code /events}. Consumed cross-service:
 * <ul>
 *   <li>{@code GET /events/{eventId}/attendance-summary} — single event,
 *       cf. internal-endpoints.md entry #1.</li>
 *   <li>{@code GET /events/_bulk-attendance-summary?ids=...} — bulk
 *       lookup (Décision I finalization-ultimate, replaces the legacy
 *       cross-schema {@code AttendanceStub.countGroupedByStatus(...)}).
 *       The {@code _bulk-} prefix avoids ambiguity with the
 *       path-param route above.</li>
 * </ul>
 *
 * <p>Not exposed via Kong (no public route) and not in
 * {@code openapi.yaml}.
 */
@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
public class AttendanceSummaryInternalResource {

    @Inject EntityManager em;

    @GET
    @Path("/{eventId}/attendance-summary")
    @PermitAll
    @Internal
    public AttendanceSummary getAttendanceSummary(@PathParam("eventId") long eventId) {
        long attending = Attendance.count("eventId = ?1 and status = ?2",
                eventId, AttendanceStatus.ATTENDING);
        long waitlisted = Attendance.count("eventId = ?1 and status = ?2",
                eventId, AttendanceStatus.WAITLISTED);
        return AttendanceSummary.of(attending, waitlisted);
    }

    @GET
    @Path("/_bulk-attendance-summary")
    @PermitAll
    @Internal
    public Map<Long, AttendanceSummary> getBulkAttendanceSummary(
            @QueryParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> attending = Attendance.countGroupedByStatus(
                ids, AttendanceStatus.ATTENDING, em);
        Map<Long, Long> waitlisted = Attendance.countGroupedByStatus(
                ids, AttendanceStatus.WAITLISTED, em);
        Map<Long, AttendanceSummary> result = new HashMap<>();
        for (Long id : ids) {
            result.put(id, AttendanceSummary.of(
                    attending.getOrDefault(id, 0L),
                    waitlisted.getOrDefault(id, 0L)));
        }
        return result;
    }
}
