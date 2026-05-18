package ch.unige.events.user.resource;

import ch.unige.events.shared.domain.dto.IdProjection;
import ch.unige.events.shared.jaxrs.Internal;
import ch.unige.events.user.entity.User;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Internal endpoint resolving an Auth0 subject claim ({@code auth0Id}) to
 * the matching {@link User} UUID. Primary consumer (SCRUM-99) :
 * notification-service ({@code NotificationService.listMine / markRead /
 * markAllRead}) needs to translate {@code SecurityIdentity.getPrincipal().getName()}
 * into the {@code notifications.user_id} stored UUID — but its own DB has
 * no {@code users} table.
 *
 * <p>Gated by {@link Internal} : {@link ch.unige.events.shared.jaxrs.InternalTokenFilter}
 * rejects every request without a valid {@code X-Internal-Token} with the
 * <em>same</em> {@code 404} envelope as an unknown {@code auth0Id} — no
 * oracle on token validity vs identity existence.
 *
 * <p>Cf. {@code backend/docs/internal-endpoints.md} entry #9.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserAuth0IdInternalResource {

    @GET
    @Path("/_internal-by-auth0-id/{auth0Id}")
    @PermitAll
    @Internal
    public IdProjection getByAuth0Id(@PathParam("auth0Id") String auth0Id) {
        return User.findByAuth0Id(auth0Id)
                .map(u -> new IdProjection(u.id))
                .orElseThrow(NotFoundException::new);
    }
}
