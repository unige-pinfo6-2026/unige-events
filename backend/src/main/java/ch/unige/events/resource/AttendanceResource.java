package ch.unige.events.resource;

import ch.unige.events.dto.attendance.AttendanceDTO;
import ch.unige.events.dto.attendance.AttendanceRequest;
import ch.unige.events.service.AttendanceService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AttendanceResource {

    private final AttendanceService attendanceService;
    private final SecurityIdentity identity;

    @Inject
    public AttendanceResource(AttendanceService attendanceService,
                              SecurityIdentity identity) {
        this.attendanceService = attendanceService;
        this.identity = identity;
    }

    @POST
    @Path("/{id}/attend")
    @Authenticated
    public Response attend(@PathParam("id") Long id,
                           @NotNull @Valid AttendanceRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        AttendanceDTO dto = attendanceService.attend(auth0Id, id, request.status());
        return Response.ok(dto).build();
    }

    @DELETE
    @Path("/{id}/attend")
    @Authenticated
    public Response removeAttendance(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        attendanceService.removeAttendance(auth0Id, id);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/attendees")
    @Authenticated
    public List<AttendanceDTO> getAttendees(
            @PathParam("id") Long id,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        return attendanceService.getAttendees(auth0Id, id, page, size);
    }
}
