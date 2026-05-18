package ch.unige.events.engagement.comment.service;

import ch.unige.events.engagement.comment.dto.CommentLikeResponse;
import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.engagement.comment.entity.CommentLike;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.projections.CallerIdentity;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;
import java.util.UUID;

/**
 * SCRUM-144 — like / unlike of comments with strictly idempotent semantics.
 *
 * <p><strong>Idempotent semantics (Décision I).</strong>
 * <ul>
 *   <li>{@link #like(Long)} : first POST → 201 + likeCount incremented ; second
 *       POST from the same user → 200 + likeCount unchanged (no double-increment).
 *       Race-safe via UK + {@code PersistenceException} catch.</li>
 *   <li>{@link #unlike(Long)} : DELETE removes the row if present, 204 either way
 *       (no error on un-liked target). {@code likeCount} decremented only when a
 *       row was actually removed.</li>
 * </ul>
 *
 * <p>Both mutations happen in a single {@code @Transactional} method so the
 * {@code likeCount} counter never drifts from the count of rows in
 * {@code comment_likes} (atomicity at the JTA level — Décision I).
 *
 * <p>Visibility delegated to event-service via
 * {@link EventServiceClient#getByIdWithCoOrgCheck(Long, UUID)} (cascade
 * ISSUE-92 + SCRUM-136). Comments on invisible events → 404 anti-oracle.
 * Applied on {@code like()} only ; {@code unlike()} runs unconditionally
 * because the caller is only removing their own state (no leak).
 */
@ApplicationScoped
public class CommentLikeService {

    private final CallerIdentity callerIdentity;
    private final EventServiceClient eventClient;
    private final EntityManager entityManager;

    @Inject
    public CommentLikeService(CallerIdentity callerIdentity,
                              @RestClient EventServiceClient eventClient,
                              EntityManager entityManager) {
        this.callerIdentity = callerIdentity;
        this.eventClient = eventClient;
        this.entityManager = entityManager;
    }

    /**
     * Like the given comment idempotently. Returns a {@code LikeResult} carrying
     * the post-mutation {@code likeCount} plus the {@code created} flag so the
     * resource can pick between 201 (fresh like) and 200 (idempotent re-POST).
     */
    @Transactional
    public LikeResult like(Long commentId) {
        Comment comment = loadCommentOrThrow(commentId);
        UUID callerUuid = callerIdentity.requireUuid();
        assertEventVisible(comment.eventId, callerUuid);

        // Pre-check the row to avoid the UK race in the happy idempotent path.
        Optional<CommentLike> existing = CommentLike.findByCommentAndUser(commentId, callerUuid);
        if (existing.isPresent()) {
            return new LikeResult(false, comment.likeCount);
        }

        CommentLike row = new CommentLike();
        row.commentId = commentId;
        row.userId = callerUuid;
        try {
            row.persist();
            // Force the UK constraint violation here (not at commit-time) so we
            // can catch it cleanly and return idempotent 200.
            entityManager.flush();
        } catch (PersistenceException e) {
            // Race-safe path : a concurrent like for the same (commentId, userId)
            // beat us to the INSERT. The row exists now ; do NOT increment counter.
            Log.debugf("[COMMENT_LIKE_RACE] commentId=" + commentId + " userId=" + callerUuid + " concurrent insert caught (uq_comment_like)");
            return new LikeResult(false, comment.likeCount);
        }

        // Atomic increment in the same transaction. Explicit JPQL via
        // EntityManager (pattern miroir Notification.markAllReadByUser
        // SCRUM-99 phase 1) — avoids Sonar S3252 false-positive on the
        // Panache enhanced static call.
        entityManager.createQuery("UPDATE Comment c SET c.likeCount = c.likeCount + 1 WHERE c.id = :id")
                .setParameter("id", commentId)
                .executeUpdate();
        // Refresh the in-memory value to reflect the just-committed counter.
        return new LikeResult(true, comment.likeCount + 1);
    }

    /**
     * Unlike the given comment idempotently. 204 either way ; decrements
     * {@code likeCount} only when a row was actually removed.
     */
    @Transactional
    public void unlike(Long commentId) {
        Comment comment = loadCommentOrThrow(commentId);
        UUID callerUuid = callerIdentity.requireUuid();
        // No visibility check on unlike — removing your own state has no leak.

        long deleted = CommentLike.delete("commentId = ?1 and userId = ?2", commentId, callerUuid);

        if (deleted > 0) {
            // Atomic decrement bounded at 0 (defensive — should not drift in
            // practice because every CommentLike row goes through this service).
            entityManager.createQuery(
                    "UPDATE Comment c SET c.likeCount = CASE WHEN c.likeCount > 0"
                            + " THEN c.likeCount - 1 ELSE 0 END WHERE c.id = :id")
                    .setParameter("id", commentId)
                    .executeUpdate();
            Log.debugf("[COMMENT_UNLIKE] commentId=" + commentId + " userId=" + callerUuid + " removed=" + deleted);
        }
        // Touch comment so static analyzers don't flag the unused load.
        if (comment.id == null) {
            Log.warnf("[COMMENT_UNLIKE_STATE] commentId=" + commentId + " loaded with null id — unexpected");
        }
    }

    private Comment loadCommentOrThrow(Long commentId) {
        return Comment.<Comment>findByIdOptional(commentId)
                .orElseThrow(NotFoundException::new);
    }

    private void assertEventVisible(Long eventId, UUID callerUuid) {
        if (eventId == null || callerUuid == null) {
            throw new NotFoundException();
        }
        // Server-side cascade ISSUE-92 + SCRUM-136 awareness ; null fallback
        // (CB open / event invisible / event hard-deleted) → 404 anti-oracle.
        EventDTO event = eventClient.getByIdWithCoOrgCheck(eventId, callerUuid);
        if (event == null) {
            throw new NotFoundException();
        }
    }

    /**
     * Result of a {@link #like(Long)} call : {@code created=true} on a
     * fresh insert (→ 201 response), {@code false} on the idempotent
     * re-POST path (→ 200 response).
     */
    public record LikeResult(boolean created, int likeCount) {
        public CommentLikeResponse toResponse() {
            return CommentLikeResponse.liked(likeCount);
        }
    }
}
