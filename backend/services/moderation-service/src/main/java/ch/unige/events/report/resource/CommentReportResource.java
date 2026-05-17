package ch.unige.events.report.resource;

import ch.unige.events.report.dto.CreateReportRequest;
import ch.unige.events.report.dto.ReportDTO;
import ch.unige.events.report.service.ReportService;
import ch.unige.events.shared.ratelimit.PerUserRateLimit;

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

/**
 * SCRUM-144 — endpoint to report a comment (Décision N).
 *
 * <p>Split from {@link ReportResource @Path("/events")} into its own class
 * because the path roots differ (Décision N — pattern split aligned with
 * {@code CommentLikeResource} étape 9). JAX-RS distinguishes the two by
 * sub-path : {@code /events/{id}/report} vs {@code /comments/{id}/report}.
 *
 * <p>The rate-limit bucket {@code reports.commentCreate} (Décision U, max=5
 * over 60 s) is applied on POST only ; anti-spam back-pressure on a write
 * surface that admins must triage.
 *
 * <p>Anti-oracle ISSUE-92 cascade : a 404 from the {@code _internal-visibility}
 * downstream call (comment inexistant OR event invisible OR token mismatch)
 * surfaces here as the same {@code 404 not_found} envelope — never 403 — so a
 * legitimate caller cannot distinguish "no such comment" from "comment on an
 * event you can't see". The two-hop chain preserves the invariant.
 */
@Path("/comments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CommentReportResource {

    private final ReportService reportService;
    private final SecurityIdentity identity;

    @Inject
    public CommentReportResource(ReportService reportService, SecurityIdentity identity) {
        this.reportService = reportService;
        this.identity = identity;
    }

    @POST
    @Path("/{commentId}/report")
    @Authenticated
    @PerUserRateLimit(name = "reports.commentCreate", max = 5, windowSeconds = 60)
    public Response reportComment(@PathParam("commentId") Long commentId,
                                  @Valid @NotNull CreateReportRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        ReportDTO created = reportService.createForComment(commentId, auth0Id, request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }
}
