package ch.unige.events.engagement.comment.service;

import ch.unige.events.shared.error.ApiErrorResponse;
import ch.unige.events.engagement.comment.dto.CommentDTO;
import ch.unige.events.engagement.comment.dto.CreateCommentRequest;
import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.projections.Auth0IdResolver;
import ch.unige.events.shared.kafka.events.CommentCreatedEvent;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Same contract as the legacy CommentService.
 *
 * <p>Étape 3.1 finalization-ultimate (STUB-001 / Décisions A, F, G):
 * the cascade SCRUM-136 + ISSUE-92 anti-oracle are now delegated to
 * event-service via {@link EventServiceClient#getByIdWithCoOrgCheck}
 * (self-check authentifié — SEC-002 / Décision C). The "is the author
 * an organizer?" annotation on listed comments is computed via
 * {@link EventServiceClient#getOrganizerUuids} (Décision G — single
 * REST call replacing N+1 self-checks). User enrichment uses
 * {@link UserServiceClient#getById}.
 */
@ApplicationScoped
public class CommentService {

    @Inject SecurityIdentity identity;
    /**
     * Lazy via {@link Instance} so {@code @QuarkusTest} runs (which set
     * {@code quarkus.oidc.enabled=false}) don't have to resolve the
     * JsonWebToken bean at startup. Same pattern as user-service's
     * UserResource.
     */
    @Inject Instance<JsonWebToken> jwt;
    @Inject jakarta.enterprise.event.Event<CommentCreatedEvent> commentCreatedEvent;

    @Inject @RestClient EventServiceClient eventClient;
    @Inject @RestClient UserServiceClient userClient;

    private JsonWebToken jwt() {
        return jwt.isResolvable() ? jwt.get() : null;
    }

    @Transactional
    public CommentDTO post(String auth0Id, Long eventId, CreateCommentRequest request) {
        boolean isAdmin = identity.hasRole("ADMIN");
        var event = assertEventVisibleAndLoad(eventId, auth0Id, isAdmin);

        if (event.status() == EventStatus.DRAFT) {
            throw badRequest("cannot_comment_draft_event",
                    "An event must be PUBLISHED before it accepts comments.");
        }
        if (event.status() == EventStatus.CANCELLED) {
            throw badRequest("cannot_comment_cancelled_event",
                    "Cannot comment a cancelled event.");
        }
        if (event.status() == EventStatus.EXPIRED) {
            throw badRequest("cannot_comment_expired_event",
                    "Cannot comment an expired event.");
        }

        UUID authorId = Auth0IdResolver.resolveUserUuid(jwt());
        if (authorId == null) {
            throw new NotFoundException(
                    "User profile not found — call GET /users/me first");
        }

        Comment parent = null;
        if (request.parentCommentId() != null) {
            parent = Comment.<Comment>findByIdOptional(request.parentCommentId())
                    .orElseThrow(() -> notFound("parent_comment_not_found",
                            "The parent comment does not exist."));
            if (parent.eventId == null || !parent.eventId.equals(eventId)) {
                throw unprocessable("parent_comment_not_in_event",
                        "The parent comment belongs to a different event.");
            }
            if (parent.parentComment != null) {
                throw unprocessable("replies_too_deep",
                        "Replies are limited to one level of depth.");
            }
        }

        Comment comment = new Comment();
        comment.eventId = eventId;
        comment.authorId = authorId;
        comment.parentComment = parent;
        comment.content = request.content().strip();
        comment.likeCount = 0;
        comment.persist();

        // CDI fire — bridge publishes comments.created AFTER_SUCCESS.
        commentCreatedEvent.fire(CommentCreatedEvent.created(
                comment.id, eventId, authorId, parent != null ? parent.id : null));

        boolean authorIsOrganizer = isCreatorOrAcceptedCoOrganizer(event, authorId);
        UserPublicResponse author = safeGetUser(authorId);
        return CommentDTO.from(comment, author, authorIsOrganizer);
    }

    @Transactional
    public void delete(String auth0Id, Long commentId) {
        Comment comment = Comment.<Comment>findByIdOptional(commentId)
                .orElseThrow(() -> notFound("comment_not_found",
                        "The comment does not exist."));

        boolean isAdmin = identity.hasRole("ADMIN");
        UUID callerUuid = Auth0IdResolver.resolveUserUuid(jwt());
        boolean isAuthor = callerUuid != null
                && comment.authorId != null
                && callerUuid.equals(comment.authorId);
        boolean isOrganizer = false;
        if (!isAdmin && !isAuthor && callerUuid != null && comment.eventId != null) {
            isOrganizer = computeOrganizerUserIds(comment.eventId).contains(callerUuid);
        }

        if (!isAdmin && !isAuthor && !isOrganizer) {
            throw forbidden("forbidden",
                    "Only the author, an event organizer or an admin can delete this comment.");
        }

        comment.delete();
    }

    public List<CommentDTO> getByEvent(Long eventId, String auth0Id, int page, int size) {
        boolean isAdmin = auth0Id != null && identity.hasRole("ADMIN");
        assertEventVisibleAndLoad(eventId, auth0Id, isAdmin);

        List<Comment> topLevels = Comment.<Comment>find(
                "eventId = ?1 and parentComment is null order by createdAt desc, id desc",
                eventId
        ).page(page, size).list();

        if (topLevels.isEmpty()) {
            return List.of();
        }

        List<Long> topLevelIds = topLevels.stream().map(c -> c.id).toList();
        List<Comment> replies = Comment.list(
                "parentComment.id in ?1 order by createdAt asc, id asc",
                topLevelIds
        );
        Map<Long, List<Comment>> repliesByParent = replies.stream()
                .collect(Collectors.groupingBy(r -> r.parentComment.id));

        Set<UUID> organizerUserIds = computeOrganizerUserIds(eventId);

        // Bulk-resolve unique author UUIDs to UserPublicResponse via REST
        // client (single hop per author across the page).
        Set<UUID> authorIds = new HashSet<>();
        for (Comment c : topLevels) {
            if (c.authorId != null) authorIds.add(c.authorId);
        }
        for (Comment r : replies) {
            if (r.authorId != null) authorIds.add(r.authorId);
        }
        Map<UUID, UserPublicResponse> usersById = new HashMap<>();
        for (UUID uid : authorIds) {
            UserPublicResponse u = safeGetUser(uid);
            if (u != null) usersById.put(uid, u);
        }

        return topLevels.stream()
                .map(top -> {
                    boolean topIsOrg = top.authorId != null
                            && organizerUserIds.contains(top.authorId);
                    List<Comment> rs = repliesByParent.getOrDefault(top.id, List.of());
                    Map<UUID, Boolean> repliesAuthorIsOrganizer = new HashMap<>();
                    for (Comment r : rs) {
                        if (r.authorId != null) {
                            repliesAuthorIsOrganizer.put(
                                    r.authorId,
                                    organizerUserIds.contains(r.authorId));
                        }
                    }
                    return CommentDTO.fromTopLevelWithReplies(
                            top,
                            usersById.get(top.authorId),
                            rs,
                            usersById,
                            topIsOrg,
                            repliesAuthorIsOrganizer);
                })
                .toList();
    }

    /**
     * ISSUE-92 anti-oracle visibility check delegated to event-service
     * via {@code GET /events/{id}?check-co-org-of=&lt;UUID&gt;}. Provider
     * applies:
     * <ul>
     *   <li>Event missing → 404.</li>
     *   <li>Event {@code BANNED} → 404 even for admins (SCRUM-97).</li>
     *   <li>Event not {@code PUBLISHED}, caller is neither admin nor creator
     *       nor ACCEPTED co-organizer → 404 (same envelope as missing).</li>
     * </ul>
     *
     * <p>The REST client fallback returns {@code null} on circuit-breaker
     * trip — propagated as a {@link NotFoundException} so the consumer
     * sees a uniform 404.
     */
    private ch.unige.events.shared.domain.dto.EventDTO assertEventVisibleAndLoad(
            Long eventId, String auth0Id, boolean isAdmin) {
        UUID callerUuid = (auth0Id != null) ? Auth0IdResolver.resolveUserUuid(jwt()) : null;
        ch.unige.events.shared.domain.dto.EventDTO event = (callerUuid != null)
                ? eventClient.getByIdWithCoOrgCheck(eventId, callerUuid)
                : eventClient.getById(eventId);
        if (event == null) {
            throw new NotFoundException();
        }
        // Provider already applied the anti-oracle ISSUE-92 server-side.
        // Defense in depth: if BANNED somehow leaked, still treat as 404.
        if (event.status() == EventStatus.BANNED) {
            throw new NotFoundException();
        }
        if (event.status() != EventStatus.PUBLISHED && !isAdmin
                && !isCreatorOrAcceptedCoOrganizer(event, callerUuid)) {
            throw new NotFoundException();
        }
        return event;
    }

    /** SCRUM-136 cascade — creator OR ACCEPTED co-organizer. */
    private boolean isCreatorOrAcceptedCoOrganizer(
            ch.unige.events.shared.domain.dto.EventDTO event, UUID callerUuid) {
        if (event == null || callerUuid == null) {
            return false;
        }
        if (callerUuid.equals(event.creatorId())) {
            return true;
        }
        Boolean checked = event.coOrganizerOf();
        if (checked != null && checked) {
            return true;
        }
        // Fallback when the event was loaded without ?check-co-org-of=.
        return computeOrganizerUserIds(event.id()).contains(callerUuid);
    }

    /**
     * The set of "organizer" user IDs for an event = creator + ACCEPTED
     * co-organizers. Used by the listing to flag {@code authorIsOrganizer}.
     * Décision G: single REST call to {@code GET /events/{id}/organizer-uuids}.
     */
    private Set<UUID> computeOrganizerUserIds(Long eventId) {
        return new HashSet<>(eventClient.getOrganizerUuids(eventId));
    }

    private UserPublicResponse safeGetUser(UUID userId) {
        if (userId == null) {
            return null;
        }
        try {
            return userClient.getById(userId);
        } catch (RuntimeException e) {
            // Fallback returns null already; this catch is defense in depth
            // for unexpected runtime issues during enrichment.
            return null;
        }
    }

    static WebApplicationException badRequest(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    static WebApplicationException unprocessable(String error, String message) {
        return new WebApplicationException(
                Response.status(422)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    static WebApplicationException forbidden(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.FORBIDDEN)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    static WebApplicationException notFound(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.NOT_FOUND)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }
}
