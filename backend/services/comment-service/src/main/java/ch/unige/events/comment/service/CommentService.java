package ch.unige.events.comment.service;

import ch.unige.events.comment.dto.ApiErrorResponse;
import ch.unige.events.comment.dto.CommentDTO;
import ch.unige.events.comment.dto.CreateCommentRequest;
import ch.unige.events.comment.entity.Comment;
import ch.unige.events.comment.entity.EventCoOrganizerStub;
import ch.unige.events.comment.entity.EventStatus;
import ch.unige.events.comment.entity.EventStub;
import ch.unige.events.comment.entity.UserStub;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

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
 * <p>The {@code EventService.getById} cascade and the SCRUM-136 cascade
 * (creator OR ACCEPTED co-organizer) are inlined here — the canonical
 * implementations live in event-service / co-organizer-service which
 * haven't been extracted yet. Once they ship, the two helpers below
 * become REST clients :
 * <ul>
 *   <li>{@code GET /events/{id}} on event-service — replaces
 *       {@link #assertEventVisibleAndLoad}.</li>
 *   <li>{@code GET /events/{id}/co-organizers/check?userId=} on
 *       co-organizer-service — replaces
 *       {@link #isCreatorOrAcceptedCoOrganizer}.</li>
 * </ul>
 */
@ApplicationScoped
public class CommentService {

    @Inject SecurityIdentity identity;

    @Transactional
    public CommentDTO post(String auth0Id, Long eventId, CreateCommentRequest request) {
        boolean isAdmin = identity.hasRole("ADMIN");
        EventStub event = assertEventVisibleAndLoad(eventId, auth0Id, isAdmin);

        if (event.status == EventStatus.DRAFT) {
            throw badRequest("cannot_comment_draft_event",
                    "An event must be PUBLISHED before it accepts comments.");
        }
        if (event.status == EventStatus.CANCELLED) {
            throw badRequest("cannot_comment_cancelled_event",
                    "Cannot comment a cancelled event.");
        }
        if (event.status == EventStatus.EXPIRED) {
            throw badRequest("cannot_comment_expired_event",
                    "Cannot comment an expired event.");
        }

        UserStub author = UserStub.findByAuth0Id(auth0Id)
                .orElseThrow(() -> new NotFoundException(
                        "User profile not found — call GET /users/me first"));

        Comment parent = null;
        if (request.parentCommentId() != null) {
            parent = Comment.<Comment>findByIdOptional(request.parentCommentId())
                    .orElseThrow(() -> notFound("parent_comment_not_found",
                            "The parent comment does not exist."));
            if (parent.event == null || !parent.event.id.equals(eventId)) {
                throw unprocessable("parent_comment_not_in_event",
                        "The parent comment belongs to a different event.");
            }
            if (parent.parentComment != null) {
                throw unprocessable("replies_too_deep",
                        "Replies are limited to one level of depth.");
            }
        }

        Comment comment = new Comment();
        comment.event = event;
        comment.author = author;
        comment.parentComment = parent;
        comment.content = request.content().strip();
        comment.likeCount = 0;
        comment.persist();

        boolean authorIsOrganizer = isCreatorOrAcceptedCoOrganizer(event, author);
        return CommentDTO.from(comment, authorIsOrganizer);
    }

    @Transactional
    public void delete(String auth0Id, Long commentId) {
        Comment comment = Comment.<Comment>findByIdOptional(commentId)
                .orElseThrow(() -> notFound("comment_not_found",
                        "The comment does not exist."));

        boolean isAdmin = identity.hasRole("ADMIN");
        boolean isAuthor = comment.author != null
                && comment.author.auth0Id != null
                && comment.author.auth0Id.equals(auth0Id);
        boolean isOrganizer = isCreatorOrAcceptedCoOrganizer(comment.event, auth0Id);

        if (!isAdmin && !isAuthor && !isOrganizer) {
            throw forbidden("forbidden",
                    "Only the author, an event organizer or an admin can delete this comment.");
        }

        comment.delete();
    }

    public List<CommentDTO> getByEvent(Long eventId, String auth0Id, int page, int size) {
        boolean isAdmin = auth0Id != null && identity.hasRole("ADMIN");
        EventStub event = assertEventVisibleAndLoad(eventId, auth0Id, isAdmin);

        List<Comment> topLevels = Comment.<Comment>find(
                "event.id = ?1 and parentComment is null order by createdAt desc, id desc",
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

        Set<UUID> organizerUserIds = computeOrganizerUserIds(event);

        return topLevels.stream()
                .map(top -> {
                    boolean topIsOrg = top.author != null
                            && organizerUserIds.contains(top.author.id);
                    List<Comment> rs = repliesByParent.getOrDefault(top.id, List.of());
                    Map<UUID, Boolean> repliesAuthorIsOrganizer = new HashMap<>();
                    for (Comment r : rs) {
                        if (r.author != null) {
                            repliesAuthorIsOrganizer.put(
                                    r.author.id,
                                    organizerUserIds.contains(r.author.id));
                        }
                    }
                    return CommentDTO.fromTopLevelWithReplies(
                            top, rs, topIsOrg, repliesAuthorIsOrganizer);
                })
                .toList();
    }

    /**
     * ISSUE-92 anti-oracle visibility check inlined from
     * {@code EventService.getById}.
     *
     * <ul>
     *   <li>Event missing → 404.</li>
     *   <li>Event {@code BANNED} → 404 even for admins (SCRUM-97).</li>
     *   <li>Event not {@code PUBLISHED}, caller is neither admin nor creator
     *       nor ACCEPTED co-organizer → 404 (same envelope as missing).</li>
     * </ul>
     */
    private EventStub assertEventVisibleAndLoad(Long eventId, String auth0Id, boolean isAdmin) {
        EventStub event = EventStub.<EventStub>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);
        if (event.status == EventStatus.BANNED) {
            throw new NotFoundException();
        }
        if (event.status != EventStatus.PUBLISHED && !isAdmin
                && !isCreatorOrAcceptedCoOrganizer(event, auth0Id)) {
            throw new NotFoundException();
        }
        return event;
    }

    /** SCRUM-136 cascade — creator OR ACCEPTED co-organizer. */
    private boolean isCreatorOrAcceptedCoOrganizer(EventStub event, String auth0Id) {
        if (event == null || auth0Id == null) {
            return false;
        }
        UserStub caller = UserStub.findByAuth0Id(auth0Id).orElse(null);
        if (caller == null) {
            return false;
        }
        return isCreatorOrAcceptedCoOrganizer(event, caller);
    }

    private boolean isCreatorOrAcceptedCoOrganizer(EventStub event, UserStub caller) {
        if (event == null || caller == null) {
            return false;
        }
        if (caller.id.equals(event.creatorId)) {
            return true;
        }
        return EventCoOrganizerStub.isAcceptedFor(event.id, caller.id);
    }

    /**
     * The set of "organizer" user IDs for an event = creator + ACCEPTED
     * co-organizers. Used by the listing to flag {@code authorIsOrganizer}.
     */
    private static Set<UUID> computeOrganizerUserIds(EventStub event) {
        Set<UUID> ids = new HashSet<>();
        if (event.creatorId != null) {
            ids.add(event.creatorId);
        }
        ids.addAll(EventCoOrganizerStub.findAcceptedUserIdsForEvent(event.id));
        return ids;
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
