package ch.unige.events.event.share.resource;

import ch.unige.events.event.share.dto.ShareResponse;
import ch.unige.events.event.share.service.ShareService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Authenticated endpoint that returns the share URL + short code for an event.
 * Was a method on FavoriteResource in the legacy monolith ; now its own
 * resource in event-service (co-located post-finalization). Path = /api/events/{id}/share.
 */
@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
public class ShareResource {

    private final ShareService shareService;

    @Inject
    public ShareResource(ShareService shareService) {
        this.shareService = shareService;
    }

    @GET
    @Path("/{id}/share")
    @Authenticated
    public Response getShareInfo(@PathParam("id") Long id) {
        ShareResponse response = shareService.getShareInfo(id);
        return Response.ok(response).build();
    }
}
