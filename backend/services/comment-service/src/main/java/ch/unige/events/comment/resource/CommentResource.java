package ch.unige.events.comment.resource;

import ch.unige.events.comment.dto.CommentDTO;
import ch.unige.events.comment.dto.CreateCommentRequest;
import ch.unige.events.comment.service.CommentService;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Comment endpoints rooted under {@code /events}. The complementary
 * {@code DELETE /comments/{id}} lives in {@link CommentDirectResource}.
 *
 * <p>The legacy POST handler decorates with
 * {@code @PerUserRateLimit(name = "comments.post", max = 10)} —
 * intentionally not duplicated in S8 (cf. PR 3 commit message).
 */
@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CommentResource {

    private final SecurityIdentity identity;
    private final CommentService commentService;

    @Inject
    public CommentResource(SecurityIdentity identity, CommentService commentService) {
        this.identity = identity;
        this.commentService = commentService;
    }

    @POST
    @Path("/{eventId}/comments")
    @Authenticated
    public Response postComment(@PathParam("eventId") Long eventId,
                                @Valid @NotNull CreateCommentRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        CommentDTO created = commentService.post(auth0Id, eventId, request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{eventId}/comments")
    @PermitAll
    public List<CommentDTO> getEventComments(
            @PathParam("eventId") Long eventId,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
        return commentService.getByEvent(eventId, auth0Id, page, size);
    }
}
