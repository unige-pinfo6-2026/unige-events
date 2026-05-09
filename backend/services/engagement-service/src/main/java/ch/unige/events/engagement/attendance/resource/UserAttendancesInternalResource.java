package ch.unige.events.engagement.attendance.resource;

import ch.unige.events.engagement.attendance.dto.AttendanceDTO;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.engagement.attendance.service.AttendanceService;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

/**
 * Internal endpoint at {@code GET /users/{id}/attendances?status=ATTENDING}.
 * Consumed cross-service by user-service (calendar ICS feed) via
 * {@link ch.unige.events.shared.client.EngagementServiceClient#getUserAttendances}.
 *
 * <p>Not exposed via Kong (no public route) and not in {@code openapi.yaml} —
 * cf. {@code backend/docs/internal-endpoints.md} entry #4.
 *
 * <p>Décision B finalization-ultimate (REST-002 P0): this resource is the
 * provider counterpart of {@code EngagementServiceClient.getUserAttendances()}.
 * Without it, the cross-service call 404s at runtime.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserAttendancesInternalResource {

    @Inject
    AttendanceService attendanceService;

    @GET
    @Path("/{id}/attendances")
    @PermitAll
    public List<AttendanceDTO> getUserAttendances(
            @PathParam("id") UUID userId,
            @QueryParam("status") AttendanceStatus status) {
        return attendanceService.findByUser(userId, status);
    }
}
