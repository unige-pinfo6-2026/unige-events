package ch.unige.events.event.attachment.resource;

import ch.unige.events.event.attachment.dto.AttachmentDTO;
import ch.unige.events.event.attachment.dto.AttachmentDownload;
import ch.unige.events.event.attachment.service.EventAttachmentService;
import ch.unige.events.shared.ratelimit.PerUserRateLimit;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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

    /**
     * SCRUM-149 follow-up — public download endpoint. Proxies the S3 object
     * back through the API so the browser hits a same-origin URL with a
     * proper {@code Content-Disposition: attachment} header, instead of
     * navigating to the internal {@code minio:9000} URL (which is not
     * reachable from outside the cluster and which the browser would open
     * inline anyway because {@code <a download>} is ignored cross-origin).
     *
     * <p>Public — attachments inherit the visibility of the event detail
     * view (anyone can see the {@code Documents} section, auth or not).
     * Anti-oracle 404 if {@code attachmentId} belongs to another event.
     */
    @GET
    @Path("/{eventId}/attachments/{attachmentId}/download")
    public Response downloadAttachment(@PathParam("eventId") Long eventId,
                                       @PathParam("attachmentId") Long attachmentId) {
        AttachmentDownload payload = service.download(eventId, attachmentId);
        if (payload == null) {
            throw new NotFoundException();
        }
        return Response.ok(payload.bytes())
                .type(payload.mimeType())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(payload.fileName()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .build();
    }

    /**
     * RFC 6266 — {@code attachment} disposition with both a quoted-string
     * fallback (ASCII only) and the extended {@code filename*} form
     * (UTF-8 percent-encoded) so non-ASCII filenames survive intact.
     */
    static String contentDisposition(String fileName) {
        String ascii = fileName
                .replaceAll("[^\\x20-\\x7E]", "_")
                .replace("\"", "");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
