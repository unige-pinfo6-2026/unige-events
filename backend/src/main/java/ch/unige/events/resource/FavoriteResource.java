package ch.unige.events.resource;

import ch.unige.events.dto.event.ShareResponse;
import ch.unige.events.service.FavoriteService;
import ch.unige.events.service.ShareService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FavoriteResource {

    private final FavoriteService favoriteService;
    private final ShareService shareService;
    private final SecurityIdentity identity;

    @Inject
    public FavoriteResource(FavoriteService favoriteService,
                             ShareService shareService,
                             SecurityIdentity identity) {
        this.favoriteService = favoriteService;
        this.shareService = shareService;
        this.identity = identity;
    }

    @POST
    @Path("/{id}/favorite")
    @Authenticated
    public Response addFavorite(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        favoriteService.addFavorite(auth0Id, id);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}/favorite")
    @Authenticated
    public Response removeFavorite(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        favoriteService.removeFavorite(auth0Id, id);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/share")
    @Authenticated
    public Response getShareInfo(@PathParam("id") Long id) {
        ShareResponse shareResponse = shareService.getShareInfo(id);
        return Response.ok(shareResponse).build();
    }
}
