package ch.unige.events.engagement.attendance.resource;

import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.jaxrs.Timeframe;
import ch.unige.events.engagement.attendance.service.AttendanceService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code GET /api/users/{id}/participations} — the PUBLISHED events a given user
 * is registered to (ATTENDING), shown in the "Participations publiques" section
 * of the public profile page (SCRUM-141 follow-up).
 *
 * <p>Authenticated endpoint (the profile page is behind {@code PrivateRoute} on
 * the frontend, so the caller always carries a token — same posture as
 * {@code GET /events/{id}/attendees}). The privacy gate lives in
 * {@link AttendanceService#getUserParticipationEvents}: self always allowed; a
 * non-self caller only sees a target whose profile is public — a private
 * non-owner target yields an empty list (mirrors the SCRUM-169 revision that
 * dropped the 404 anti-oracle on {@code GET /users/{id}}). Only ATTENDING
 * inscriptions to PUBLISHED events are exposed.
 *
 * <p>Coexists with {@code MyAttendancesResource} ({@code /users/me/participations})
 * — JAX-RS resolves the literal {@code /me/} path ahead of the {@code /{id}/}
 * template.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserParticipationsResource {

    private final AttendanceService attendanceService;

    @Inject
    public UserParticipationsResource(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GET
    @Path("/{id}/participations")
    @Authenticated
    public List<EventDTO> getUserParticipations(
            @PathParam("id") UUID id,
            @QueryParam("timeframe") String timeframeParam) {
        Timeframe timeframe = parseTimeframe(timeframeParam);
        return attendanceService.getUserParticipationEvents(id, timeframe);
    }

    /**
     * Same parsing rules as {@code MyAttendancesResource} — case-insensitive,
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
