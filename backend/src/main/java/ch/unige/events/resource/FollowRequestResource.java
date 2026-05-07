package ch.unige.events.resource;

import ch.unige.events.dto.follow.FollowDTO;
import ch.unige.events.entity.Follow;
import ch.unige.events.service.FollowService;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Accept/reject endpoints on follow requests. Hosted under {@code /follow-requests}
 * — separate from {@link FollowResource} because each Resource keeps a single
 * unambiguous class-level @Path.
 */
@Path("/follow-requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FollowRequestResource {

    @Inject SecurityIdentity identity;
    @Inject FollowService followService;

    @PATCH
    @Path("/{followId}/accept")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public FollowDTO acceptFollowRequest(@PathParam("followId") Long followId) {
        String auth0Id = identity.getPrincipal().getName();
        Follow row = followService.acceptRequest(auth0Id, followId);
        return FollowDTO.from(row);
    }

    @PATCH
    @Path("/{followId}/reject")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response rejectFollowRequest(@PathParam("followId") Long followId) {
        String auth0Id = identity.getPrincipal().getName();
        followService.rejectRequest(auth0Id, followId);
        return Response.noContent().build();
    }
}
