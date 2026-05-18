package ch.unige.events.engagement.comment.resource;

import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.domain.dto.CommentVisibilityProjection;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.jaxrs.Internal;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.UUID;

/**
 * SCRUM-144 — endpoint interne pour valider la visibilité d'un commentaire
 * cross-service. Consommé par moderation-service pour
 * {@code POST /comments/{commentId}/report} (Décision L).
 *
 * <p>Logique :
 * <ol>
 *   <li>Fetch {@link Comment} localement ; si absent → 404 anti-oracle.</li>
 *   <li>Délègue au cascade ISSUE-92 + SCRUM-136 d'event-service via
 *       {@link EventServiceClient#getByIdWithCoOrgCheck(Long, UUID)}. Si
 *       l'event est invisible (DRAFT non-creator, BANNED, deleted, CB open)
 *       le client retourne {@code null} → 404 anti-oracle.</li>
 *   <li>Retourne {@link CommentVisibilityProjection} avec
 *       {@code callerHasAccess=true} + {@code authorId} pour permettre au
 *       caller de vérifier la règle {@code cannot_report_own_comment}
 *       (Décision N) sans hop supplémentaire.</li>
 * </ol>
 *
 * <p>Gated by {@link Internal} : le filter
 * {@link ch.unige.events.shared.jaxrs.InternalTokenFilter} rejette tout
 * request sans {@code X-Internal-Token} valide avec un 404 envelope identique
 * (anti-oracle sur la validité du token). Pas dans {@code openapi.yaml} —
 * documenté dans {@code backend/docs/internal-endpoints.md} entry #10.
 */
@Path("/comments")
@Produces(MediaType.APPLICATION_JSON)
public class CommentVisibilityInternalResource {

    private final EventServiceClient eventClient;

    @Inject
    public CommentVisibilityInternalResource(@RestClient EventServiceClient eventClient) {
        this.eventClient = eventClient;
    }

    @GET
    @Path("/{commentId}/_internal-visibility")
    @PermitAll
    @Internal
    public CommentVisibilityProjection getVisibility(
            @PathParam("commentId") Long commentId,
            @QueryParam("callerId") UUID callerId) {
        Comment comment = Comment.<Comment>findByIdOptional(commentId)
                .orElseThrow(NotFoundException::new);
        // Propagate cascade ISSUE-92 + SCRUM-136 via event-service. If the
        // caller has no access OR the event is invisible OR CB is open → null,
        // we surface it as 404 anti-oracle (same envelope as comment unknown).
        EventDTO event = eventClient.getByIdWithCoOrgCheck(comment.eventId, callerId);
        if (event == null) {
            throw new NotFoundException();
        }
        return new CommentVisibilityProjection(comment.id, comment.eventId, comment.authorId, true);
    }
}
