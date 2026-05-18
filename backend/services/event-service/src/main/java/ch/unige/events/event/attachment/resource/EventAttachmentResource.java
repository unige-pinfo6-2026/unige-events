package ch.unige.events.event.attachment.resource;

import ch.unige.events.event.attachment.dto.AttachmentDTO;
import ch.unige.events.event.attachment.service.EventAttachmentService;
import ch.unige.events.shared.ratelimit.PerUserRateLimit;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * SCRUM-148 — multipart endpoints for event attachments (Décision R).
 *
 * <p>Resource split from {@link ch.unige.events.event.resource.EventResource}
 * (which keeps {@code /events/{id}/image} for the banner) — JAX-RS routes
 * both classes under {@code @Path("/events")} and disambiguates by
 * sub-path. The split keeps {@code EventResource} (~270 lines) from
 * accumulating yet more methods (Décision R rationale).
 *
 * <p>Rate-limit bucket {@code events.uploadAttachment} (max=10/60 s,
 * Décision U) is on POST only. DELETE is unrestricted — idempotent, low
 * cost, no incentive to spam.
 */
@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
public class EventAttachmentResource {

    private static final String ROLE_ADMIN = "ADMIN";

    private final EventAttachmentService service;
    private final SecurityIdentity identity;

    @Inject
    public EventAttachmentResource(EventAttachmentService service, SecurityIdentity identity) {
        this.service = service;
        this.identity = identity;
    }

    @POST
    @Path("/{eventId}/attachments")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Authenticated
    @PerUserRateLimit(name = "events.uploadAttachment", max = 10, windowSeconds = 60)
    public Response uploadAttachment(@PathParam("eventId") Long eventId,
                                     @RestForm("file") FileUpload file) {
        if (file == null) {
            throw new BadRequestException("Missing required form field: file");
        }
        boolean isAdmin = identity.hasRole(ROLE_ADMIN);
        AttachmentDTO dto = service.upload(eventId, file, isAdmin);
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @DELETE
    @Path("/{eventId}/attachments/{attachmentId}")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response deleteAttachment(@PathParam("eventId") Long eventId,
                                     @PathParam("attachmentId") Long attachmentId) {
        boolean isAdmin = identity.hasRole(ROLE_ADMIN);
        service.delete(eventId, attachmentId, isAdmin);
        return Response.noContent().build();
    }
}
