package ch.unige.events.user.follow.resource;

import ch.unige.events.shared.jaxrs.Internal;
import ch.unige.events.user.follow.entity.Follow;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

/**
 * Internal endpoint consumed by event-service to implement the
 * {@code followedOnly} filter on {@code GET /events} (SCRUM-168).
 *
 * <p>Returns the UUIDs of all users that {@code followerId} follows
 * with status ACCEPTED. Empty list when the user follows nobody or
 * when {@code followerId} is null.
 *
 * <p>Not exposed via Kong, not in {@code openapi.yaml}.
 * Cf. {@code backend/docs/internal-endpoints.md} entry #11.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserFollowedIdsInternalResource {

    @GET
    @Path("/_internal-followed-ids")
    @PermitAll
    @Internal
    public List<UUID> getFollowedIds(@QueryParam("followerId") UUID followerId) {
        if (followerId == null) {
            return List.of();
        }
        return Follow.findAcceptedFollowedIds(followerId);
    }
}
