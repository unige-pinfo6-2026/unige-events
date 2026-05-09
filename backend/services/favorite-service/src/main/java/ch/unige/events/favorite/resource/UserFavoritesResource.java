package ch.unige.events.favorite.resource;

import ch.unige.events.favorite.dto.EventDTO;
import ch.unige.events.favorite.service.FavoriteService;
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
 * authenticated user has favorited. In the legacy monolith this method
 * lived on {@code UserResource} ; in the microservices topology it ships
 * with favorite-service so favorites stay co-located with the data they
 * project (cf. spec § "Découpage par bounded context").
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
