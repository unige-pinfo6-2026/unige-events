package ch.unige.events.report.resource;

import ch.unige.events.report.dto.CreateReportRequest;
import ch.unige.events.report.dto.ReportDTO;
import ch.unige.events.report.service.ReportService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportResource {

    private final ReportService reportService;
    private final SecurityIdentity identity;

    @Inject
    public ReportResource(ReportService reportService, SecurityIdentity identity) {
        this.reportService = reportService;
        this.identity = identity;
    }

    @POST
    @Path("/{id}/report")
    @Authenticated
    public Response report(@PathParam("id") Long eventId,
                           @Valid @NotNull CreateReportRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        ReportDTO created = reportService.create(eventId, auth0Id, request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }
}
