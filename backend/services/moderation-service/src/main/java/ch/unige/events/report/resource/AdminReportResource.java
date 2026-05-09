package ch.unige.events.report.resource;

import ch.unige.events.report.dto.HandleReportRequest;
import ch.unige.events.report.dto.ReportDTO;
import ch.unige.events.shared.domain.enums.ReportStatus;
import ch.unige.events.report.service.ReportService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/admin/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminReportResource {

    private final ReportService reportService;
    private final SecurityIdentity identity;

    @Inject
    public AdminReportResource(ReportService reportService, SecurityIdentity identity) {
        this.reportService = reportService;
        this.identity = identity;
    }

    @GET
    public List<ReportDTO> list(
            @QueryParam("status") ReportStatus status,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        return reportService.listByStatus(status, page, size);
    }

    @PATCH
    @Path("/{id}")
    public ReportDTO handle(@PathParam("id") Long reportId,
                            @Valid @NotNull HandleReportRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        return reportService.handle(reportId, auth0Id, request);
    }
}
