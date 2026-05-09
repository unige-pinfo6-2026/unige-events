package ch.unige.events.favorite.resource;

import ch.unige.events.favorite.service.FavoriteService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
public class FavoriteResource {

    private final FavoriteService favoriteService;
    private final SecurityIdentity identity;

    @Inject
    public FavoriteResource(FavoriteService favoriteService, SecurityIdentity identity) {
        this.favoriteService = favoriteService;
        this.identity = identity;
    }

    /**
     * The legacy monolith decorates this method with {@code @PerUserRateLimit
     * (name = "events.favorite", max = 30)}. The interceptor lives in the
     * monolith module and is intentionally not duplicated in S8 — once Kong
     * routes /events/{id}/favorite here, the rate limit is temporarily
     * dropped. Restored at PR 14 (legacy-monolith removal) by porting the
     * Caffeine-based bucket into a shared library or moving it to Kong's
     * rate-limiting plugin.
     */
    @POST
    @Path("/{id}/favorite")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
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
}
