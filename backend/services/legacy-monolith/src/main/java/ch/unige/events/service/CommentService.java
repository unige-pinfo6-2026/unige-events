package ch.unige.events.service;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.dto.comment.CommentDTO;
import ch.unige.events.dto.comment.CreateCommentRequest;
import ch.unige.events.entity.Comment;
import ch.unige.events.entity.CoOrganizerStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCoOrganizer;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;

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
 * Service couche métier pour les commentaires d'événements (SCRUM-139).
 *
 * <p>La visibilité des events est déléguée intégralement à
 * {@link EventService#getById(Long, String, boolean)} (anti-oracle ISSUE-92,
 * cf. spec décision 14). La cascade d'autorisation pour {@code DELETE} repose
 * sur {@link EventService#isCreatorOrAcceptedCoOrganizerPublic(Event, String)}
 * (SCRUM-136, cf. spec décision 16).
 *
 * <p>Les helpers d'erreurs sont dupliqués localement (cf. spec décision 28) —
 * pattern aligné sur {@code ReportService} et {@code FollowService}.
 */
@ApplicationScoped
public class CommentService {

    @Inject EventService eventService;
    @Inject SecurityIdentity identity;

    // ── Mutations (toutes @Transactional) ──────────────────────────────────────

    @Transactional
    public CommentDTO post(String auth0Id, Long eventId, CreateCommentRequest request) {
        boolean isAdmin = identity.hasRole("ADMIN");

        // Garde anti-oracle ISSUE-92 : event invisible → 404 (cf. spec décision 14).
        eventService.getById(eventId, auth0Id, isAdmin);

        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        // Branchement par statut métier (cf. spec décision 15).
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

        User author = User.findByAuth0Id(auth0Id)
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

        boolean authorIsOrganizer =
                eventService.isCreatorOrAcceptedCoOrganizerPublic(event, auth0Id);
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
        boolean isOrganizer = eventService.isCreatorOrAcceptedCoOrganizerPublic(
                comment.event, auth0Id);

        if (!isAdmin && !isAuthor && !isOrganizer) {
            throw forbidden("forbidden",
                    "Only the author, an event organizer or an admin can delete this comment.");
        }

        comment.delete();
    }

    // ── Lectures (non-transactional, cf. spec décision 26) ─────────────────────

    public List<CommentDTO> getByEvent(Long eventId, String auth0Id, int page, int size) {
        boolean isAdmin = auth0Id != null && identity.hasRole("ADMIN");

        // Garde anti-oracle ISSUE-92 héritée de getById (cf. spec décision 14).
        eventService.getById(eventId, auth0Id, isAdmin);

        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

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

    private static Set<UUID> computeOrganizerUserIds(Event event) {
        Set<UUID> ids = new HashSet<>();
        if (event.creator != null && event.creator.id != null) {
            ids.add(event.creator.id);
        }
        List<EventCoOrganizer> coOrgs = EventCoOrganizer.<EventCoOrganizer>find(
                "eventId = ?1 and status = ?2",
                event.id, CoOrganizerStatus.ACCEPTED
        ).list();
        coOrgs.forEach(co -> ids.add(co.userId));
        return ids;
    }

    // ── Helpers d'erreurs (dupliqués depuis ReportService — cf. spec décision 28) ──

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
