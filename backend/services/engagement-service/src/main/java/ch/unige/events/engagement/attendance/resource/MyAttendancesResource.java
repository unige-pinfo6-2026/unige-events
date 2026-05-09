package ch.unige.events.engagement.attendance.resource;

import ch.unige.events.engagement.attendance.dto.AttendanceDTO;
import ch.unige.events.engagement.attendance.dto.EventDTO;
import ch.unige.events.engagement.attendance.entity.AttendanceStatus;
import ch.unige.events.engagement.attendance.entity.Timeframe;
import ch.unige.events.engagement.attendance.service.AttendanceService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Locale;

/**
 * {@code GET /api/users/me/attendances} and {@code /me/participations} —
 * the caller's own inscriptions. Lived on UserResource in legacy.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class MyAttendancesResource {

    private final AttendanceService attendanceService;
    private final SecurityIdentity identity;

    @Inject
    public MyAttendancesResource(AttendanceService attendanceService, SecurityIdentity identity) {
        this.attendanceService = attendanceService;
        this.identity = identity;
    }

    @GET
    @Path("/me/attendances")
    @Authenticated
    public List<AttendanceDTO> getMyAttendances() {
        return attendanceService.getMyAttendances(identity.getPrincipal().getName());
    }

    @GET
    @Path("/me/participations")
    @Authenticated
    public List<EventDTO> getMyParticipationEvents(
            @QueryParam("status") AttendanceStatus status,
            @QueryParam("timeframe") String timeframeParam) {
        Timeframe timeframe = parseTimeframe(timeframeParam);
        return attendanceService.getMyParticipationEvents(
                identity.getPrincipal().getName(), status, timeframe);
    }

    /**
     * Same parsing rules as the legacy UserResource — case-insensitive,
     * blank → null, anything else → 400 (rather than the JAX-RS default 404).
     */
    private static Timeframe parseTimeframe(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Timeframe.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid timeframe value: " + raw + " (expected upcoming or past)");
        }
    }
}
