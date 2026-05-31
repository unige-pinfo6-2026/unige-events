package ch.unige.events.engagement.comment.resource;

import ch.unige.events.engagement.comment.service.CommentService;
import ch.unige.events.shared.domain.dto.CommentContentProjection;
import ch.unige.events.shared.jaxrs.Internal;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Internal endpoints (QA bug batch, bug ③) consumed by moderation-service when
 * an admin handles a comment-bound report. Both gated {@link Internal}
 * (X-Internal-Token) — not in {@code openapi.yaml}, documented in
 * {@code backend/docs/internal-endpoints.md}.
 *
 * <ul>
 *   <li>{@code DELETE /comments/{commentId}/_internal-moderation} — hard-delete
 *       the comment when its report is REVIEWED (moderation analogue of an event
 *       BAN). 404 anti-oracle when already gone (caller treats it as idempotent
 *       success).</li>
 *   <li>{@code GET /comments/_internal-by-ids?ids=…} — batch content projection
 *       to enrich the admin reports listing. The {@code _internal-by-ids}
 *       segment is literal so it never collides with the {@code /{commentId}}
 *       path-param routes.</li>
 * </ul>
 */
@Path("/comments")
@Produces(MediaType.APPLICATION_JSON)
public class CommentModerationInternalResource {

    private final CommentService commentService;

    @Inject
    public CommentModerationInternalResource(CommentService commentService) {
        this.commentService = commentService;
    }

    @DELETE
    @Path("/{commentId}/_internal-moderation")
    @PermitAll
    @Internal
    public Response deleteForModeration(@PathParam("commentId") Long commentId) {
        commentService.deleteForModeration(commentId);
        return Response.noContent().build();
    }

    @GET
    @Path("/_internal-by-ids")
    @PermitAll
    @Internal
    public List<CommentContentProjection> getByIds(@QueryParam("ids") List<Long> ids) {
        return commentService.getContentByIds(ids);
    }
}
