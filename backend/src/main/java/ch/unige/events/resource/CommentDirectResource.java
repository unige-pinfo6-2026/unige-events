package ch.unige.events.resource;

import ch.unige.events.service.CommentService;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Direct comment endpoints rooted under {@code /comments}. Hosted separately
 * from {@link CommentResource} (which is rooted under {@code /events}) because
 * each Resource keeps a single unambiguous class-level @Path
 * (cf. spec SCRUM-139 décision 25, pattern institué par SCRUM-138 pour
 * {@code FollowResource} / {@code FollowRequestResource}).
 */
@Path("/comments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CommentDirectResource {

    private final SecurityIdentity identity;
    private final CommentService commentService;

    @Inject
    public CommentDirectResource(SecurityIdentity identity, CommentService commentService) {
        this.identity = identity;
        this.commentService = commentService;
    }

    @DELETE
    @Path("/{commentId}")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response deleteComment(@PathParam("commentId") Long commentId) {
        String auth0Id = identity.getPrincipal().getName();
        commentService.delete(auth0Id, commentId);
        return Response.noContent().build();
    }
}
