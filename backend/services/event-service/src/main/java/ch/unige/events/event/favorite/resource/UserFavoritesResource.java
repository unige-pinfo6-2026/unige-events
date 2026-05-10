package ch.unige.events.event.favorite.resource;

import ch.unige.events.event.favorite.dto.EventDTO;
import ch.unige.events.event.favorite.service.FavoriteService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * {@code GET /api/users/me/favorites} — paginated list of events the
 * authenticated user has favorited. Owned by event-service (favorite-service
 * was absorbed in finalization Étape 2.2.3, cf. sprint-context.md § Étape 23).
 * Path = /api/users/me/favorites.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserFavoritesResource {

    private final FavoriteService favoriteService;
    private final SecurityIdentity identity;

    @Inject
    public UserFavoritesResource(FavoriteService favoriteService, SecurityIdentity identity) {
        this.favoriteService = favoriteService;
        this.identity = identity;
    }

    @GET
    @Path("/me/favorites")
    @Authenticated
    public List<EventDTO> getMyFavorites(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        return favoriteService.getFavorites(auth0Id, page, size);
    }
}
