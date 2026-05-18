package ch.unige.events.engagement.comment.resource;

import ch.unige.events.engagement.comment.service.CommentLikeService;
import ch.unige.events.shared.ratelimit.PerUserRateLimit;

import io.quarkus.security.Authenticated;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * SCRUM-144 — endpoints likes/unlike de commentaires.
 *
 * <p>Resource séparée (pattern split aligné SCRUM-139) — {@code CommentLikeResource}
 * cohabite avec {@code CommentDirectResource} sur la même racine {@code @Path("/comments")} ;
 * JAX-RS distingue par sous-paths. Tests isolés via classe dédiée.
 *
 * <p>{@code @Consumes(WILDCARD)} sur les endpoints sans body (POST/DELETE) — la
 * convention REST-Assured passe systématiquement un {@code Content-Type}, qui sinon
 * tripperait un 415 avant {@code @Authenticated} (piège #6 / leçon SCRUM-99 phase 1).
 */
@Path("/comments")
@Produces(MediaType.APPLICATION_JSON)
public class CommentLikeResource {

    private final CommentLikeService commentLikeService;

    @Inject
    public CommentLikeResource(CommentLikeService commentLikeService) {
        this.commentLikeService = commentLikeService;
    }

    @POST
    @Path("/{commentId}/like")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    @PerUserRateLimit(name = "comments.like", max = 30, windowSeconds = 60)
    public Response like(@PathParam("commentId") Long commentId) {
        CommentLikeService.LikeResult result = commentLikeService.like(commentId);
        Response.Status status = result.created()
                ? Response.Status.CREATED
                : Response.Status.OK;
        return Response.status(status).entity(result.toResponse()).build();
    }

    @DELETE
    @Path("/{commentId}/like")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response unlike(@PathParam("commentId") Long commentId) {
        commentLikeService.unlike(commentId);
        return Response.noContent().build();
    }
}
