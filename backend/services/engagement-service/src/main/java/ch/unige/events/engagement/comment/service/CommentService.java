package ch.unige.events.engagement.comment.service;

import ch.unige.events.shared.error.ApiErrorResponse;
import ch.unige.events.engagement.comment.dto.CommentDTO;
import ch.unige.events.engagement.comment.dto.CreateCommentRequest;
import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.CommentContentProjection;
import ch.unige.events.shared.domain.dto.IdProjection;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.projections.CallerIdentity;
import ch.unige.events.shared.kafka.events.CommentCreatedEvent;
import ch.unige.events.shared.kafka.events.CommentMentionEvent;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
 * <p>STUB-001 / Décisions A, F, G: the cascade SCRUM-136 + ISSUE-92
 * anti-oracle are now delegated to
 * event-service via {@link EventServiceClient#getByIdWithCoOrgCheck}
 * (self-check authentifié — SEC-002 / Décision C). The "is the author
 * an organizer?" annotation on listed comments is computed via
 * {@link EventServiceClient#getOrganizerUuids} (Décision G — single
 * REST call replacing N+1 self-checks). User enrichment uses
 * {@link UserServiceClient#getById}.
 */
@ApplicationScoped
public class CommentService {

    /** Author display-label fallback, mirrors the frontend `userDisplayLabel`. */
    private static final String FALLBACK_AUTHOR_LABEL = "Un utilisateur";

    @Inject SecurityIdentity identity;
    /**
     * Lazy via {@link Instance} so {@code @QuarkusTest} runs (which set
     * {@code quarkus.oidc.enabled=false}) don't have to resolve the
     * JsonWebToken bean at startup. Same pattern as user-service's
     * UserResource.
     */
    @Inject CallerIdentity callerIdentity;
    @Inject jakarta.enterprise.event.Event<CommentCreatedEvent> commentCreatedEvent;
    @Inject jakarta.enterprise.event.Event<CommentMentionEvent> commentMentionEvent;
    @Inject MentionParser mentionParser;

    @Inject @RestClient EventServiceClient eventClient;
    @Inject @RestClient UserServiceClient userClient;

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

        UUID authorId = callerIdentity.requireUuid();
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
        // SCRUM-145: enriched payload (content + eventTitle) so the
        // notification-service consumers can parse mentions and compose
        // user-facing messages without a REST callback.
        commentCreatedEvent.fire(CommentCreatedEvent.created(
                comment.id, eventId, authorId,
                parent != null ? parent.id : null,
                comment.content, event.title()));

        boolean authorIsOrganizer = isCreatorOrAcceptedCoOrganizer(event, authorId);
        UserPublicResponse author = safeGetUser(authorId);

        // SCRUM-145 — fan-out one CommentMentionEvent per mentioned user.
        // Mirrors the FollowLifecyclePublisher pattern : the producer does
        // the heavy lifting (parse + resolve + filter) so the consumer in
        // notification-service is trivial. AFTER_SUCCESS bridge ensures we
        // never publish a phantom mention against a rolled-back comment.
        fanOutMentions(comment.id, eventId, authorId, comment.content, event.title(), author);

        return CommentDTO.from(comment, author, authorIsOrganizer);
    }

    /**
     * Parses {@code @<handle>} mentions from the comment body, batch-resolves
     * to UUIDs via the internal user-service endpoint, filters out
     * self-mentions (locked-in #11) and unknown handles, then fires one
     * {@link CommentMentionEvent} per remaining recipient. The CDI bridge
     * routes each event to {@code comments.mentions} after the JDBC
     * commit. Failures are swallowed — a missed mention notification is
     * acceptable, a 500 on the caller's POST is not.
     */
    private void fanOutMentions(long commentId, long eventId, UUID authorId,
                                String content, String eventTitle, UserPublicResponse author) {
        Set<String> handles = mentionParser.extractHandles(content);
        if (handles.isEmpty()) {
            return;
        }
        List<IdProjection> resolved;
        try {
            resolved = userClient.getByUsernames(String.join(",", handles));
        } catch (RuntimeException e) {
            Log.warnf(e, "[MENTION_RESOLVE_FAIL] comment=%d event=%d handles=%s — skipping mention fan-out",
                    commentId, eventId, handles);
            return;
        }
        if (resolved == null || resolved.isEmpty()) {
            Log.infof("[MENTION_FANOUT_NO_MATCH] comment=%d handles=%s resolved 0 users", commentId, handles);
            return;
        }
        String authorLabel = authorLabel(author);
        String safeEventTitle = (eventTitle == null || eventTitle.isBlank()) ? null : eventTitle;
        int emitted = 0;
        for (IdProjection target : resolved) {
            if (target == null || target.id() == null) {
                continue;
            }
            if (target.id().equals(authorId)) {
                continue; // self-mention skip — locked-in #11
            }
            commentMentionEvent.fire(CommentMentionEvent.of(
                    commentId, eventId, authorId, target.id(), authorLabel, safeEventTitle));
            emitted++;
        }
        Log.infof("[MENTION_FANOUT] comment=%d event=%d emitted=%d/%d (resolved %d)",
                commentId, eventId, emitted, handles.size(), resolved.size());
    }

    /**
     * Mirrors the frontend `userDisplayLabel` fallback chain : displayName →
     * "@" + username → "Un utilisateur". Used to pre-compute the label that
     * notification-service interpolates into the message template, so the
     * consumer doesn't need to hop back to user-service.
     */
    private static String authorLabel(UserPublicResponse author) {
        if (author == null) {
            return FALLBACK_AUTHOR_LABEL;
        }
        if (author.displayName() != null && !author.displayName().isBlank()) {
            return author.displayName();
        }
        if (author.username() != null && !author.username().isBlank()) {
            return "@" + author.username();
        }
        return FALLBACK_AUTHOR_LABEL;
    }

    @Transactional
    public void delete(String auth0Id, Long commentId) {
        Comment comment = Comment.<Comment>findByIdOptional(commentId)
                .orElseThrow(() -> notFound("comment_not_found",
                        "The comment does not exist."));

        boolean isAdmin = identity.hasRole("ADMIN");
        UUID callerUuid = callerIdentity.requireUuid();
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

    /**
     * Hard-deletes a comment on behalf of moderation (QA bug batch, bug ③).
     * Unlike {@link #delete}, no author/organizer/admin check is performed: the
     * caller is moderation-service over the internal channel (X-Internal-Token
     * gated), validating a comment report — the moderation analogue of an event
     * BAN. 404 anti-oracle when the comment is already gone (idempotent for the
     * caller, which swallows the NotFoundException).
     */
    @Transactional
    public void deleteForModeration(Long commentId) {
        Comment comment = Comment.<Comment>findByIdOptional(commentId)
                .orElseThrow(() -> notFound("comment_not_found", "The comment does not exist."));
        comment.delete();
    }

    /**
     * Bulk content projection by id (QA bug batch, bug ③) — feeds the admin
     * reports listing in moderation-service so a comment report renders its body
     * + deep-links to its event. Unknown / deleted ids are silently omitted.
     */
    public List<CommentContentProjection> getContentByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return Comment.<Comment>list("id in ?1", ids).stream()
                .map(c -> new CommentContentProjection(c.id, c.eventId, c.content))
                .toList();
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

        // SCRUM-144 anti N+1 — single JPQL batch on comment_likes to populate
        // CommentDTO.likedByMe for the entire page (top-level + replies) in
        // one query. Anonymous callers get an empty Set (likedByMe = false
        // everywhere — no hop). Décision K.
        Set<Long> likedIds = resolveLikedIds(auth0Id, topLevels, replies);

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
                    return CommentDTO.fromTopLevelWithRepliesAndLikes(
                            top,
                            usersById.get(top.authorId),
                            rs,
                            usersById,
                            topIsOrg,
                            repliesAuthorIsOrganizer,
                            likedIds);
                })
                .toList();
    }

    /**
     * SCRUM-144 — bulk resolution of {@code CommentDTO.likedByMe} via one JPQL
     * query on {@code comment_likes} (anti N+1, Décision K). Returns the empty
     * set for anonymous callers (no DB hit) or when the caller's UUID cannot
     * be resolved (e.g. user not provisioned).
     */
    private Set<Long> resolveLikedIds(String auth0Id, List<Comment> topLevels, List<Comment> replies) {
        if (auth0Id == null) {
            return Set.of();
        }
        UUID callerUuid = callerIdentity.getUuid();
        if (callerUuid == null) {
            return Set.of();
        }
        Set<Long> commentIds = new HashSet<>();
        for (Comment c : topLevels) {
            commentIds.add(c.id);
        }
        for (Comment r : replies) {
            commentIds.add(r.id);
        }
        return ch.unige.events.engagement.comment.entity.CommentLike
                .findLikedCommentIdsByUser(commentIds, callerUuid);
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
        UUID callerUuid = (auth0Id != null) ? callerIdentity.getUuid() : null;
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
        } catch (jakarta.ws.rs.NotFoundException e) {
            // Semantic absence: user was hard-deleted or never existed. Caller
            // already treats null as "anonymous author" — no log needed.
            return null;
        } catch (RuntimeException e) {
            // Infra failure (timeout, CB open, JSON parse error, etc.). Log so
            // ops can correlate degraded enrichment to a downstream incident.
            Log.warnf(e, "[USER_ENRICHMENT_FAIL] safeGetUser(%s) — returning null (degraded enrichment due to downstream failure)", userId);
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
