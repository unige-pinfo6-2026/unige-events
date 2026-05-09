package ch.unige.events.engagement.attendance.resource;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.engagement.attendance.entity.AttendanceStatus;
import ch.unige.events.shared.domain.dto.AttendanceSummary;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Internal endpoint at {@code GET /events/{eventId}/attendance-summary}.
 * Consumed cross-service by event-service / moderation-service via
 * {@code EngagementServiceClient.getAttendanceSummary(eventId)}.
 *
 * <p>Not exposed via Kong (no public route) and not in {@code openapi.yaml} —
 * cf. {@code backend/docs/internal-endpoints.md} entry #1.
 */
@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
public class AttendanceSummaryInternalResource {

    @GET
    @Path("/{eventId}/attendance-summary")
    @PermitAll
    public AttendanceSummary getAttendanceSummary(@PathParam("eventId") long eventId) {
        long attending = Attendance.count("eventId = ?1 and status = ?2",
                eventId, AttendanceStatus.ATTENDING);
        long waitlisted = Attendance.count("eventId = ?1 and status = ?2",
                eventId, AttendanceStatus.WAITLISTED);
        return AttendanceSummary.of(attending, waitlisted);
    }
}
