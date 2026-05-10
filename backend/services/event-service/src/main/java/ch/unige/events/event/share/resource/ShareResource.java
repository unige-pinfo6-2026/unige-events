package ch.unige.events.event.share.resource;

import ch.unige.events.event.share.dto.ShareResponse;
import ch.unige.events.event.share.service.ShareService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Authenticated endpoint that returns the share URL + short code for an event.
 * Co-located in event-service post-finalization (cf. sprint-context.md § Étape 23).
 * Path = /api/events/{id}/share.
 *
 * <p>The cascade ISSUE-92 (anti-oracle) + creator/co-organizer/admin guard is
 * applied in {@link ShareService#getShareInfo(Long, String, boolean)} so a
 * stranger cannot trigger {@code event.shareCode} generation by probing.
 */
@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
public class ShareResource {

    private static final String ROLE_ADMIN = "ADMIN";

    private final ShareService shareService;
    private final SecurityIdentity identity;

    @Inject
    public ShareResource(ShareService shareService, SecurityIdentity identity) {
        this.shareService = shareService;
        this.identity = identity;
    }

    @GET
    @Path("/{id}/share")
    @Authenticated
    public Response getShareInfo(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        boolean isAdmin = identity.hasRole(ROLE_ADMIN);
        ShareResponse response = shareService.getShareInfo(id, auth0Id, isAdmin);
        return Response.ok(response).build();
    }
}
