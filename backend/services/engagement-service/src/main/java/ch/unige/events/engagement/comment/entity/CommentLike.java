package ch.unige.events.engagement.comment.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owned by engagement-service. SCRUM-144 — backs the like/unlike behavior
 * on comments + populates {@code CommentDTO.likedByMe} via a batch lookup.
 *
 * <p>FK {@code comment_id → comments(id) ON DELETE CASCADE} : local FK
 * (both tables live in postgres-engagement). When a {@code Comment} row
 * is deleted, all its likes are garbage-collected automatically.
 *
 * <p>No FK on {@code user_id} : DB-per-service strict — the {@code users}
 * table lives in postgres-user, not postgres-engagement. UUID consistency
 * is enforced at the application layer (piège #8).
 *
 * <p>The unique constraint {@code uq_comment_like (comment_id, user_id)}
 * enforces application-level idempotence : a second POST /comments/{id}/like
 * from the same user produces a {@link jakarta.persistence.PersistenceException}
 * caught by {@code CommentLikeService} → translated to a 200 idempotent
 * response (no double-increment of {@code Comment.likeCount}).
 *
 * <p>Composite index {@code idx_comment_like_user} on {@code (user_id)}
 * serves the batch lookup {@link #findLikedCommentIdsByUser(Collection, UUID)}
 * used by {@code CommentService.getByEvent} (anti N+1 — Décision K).
 */
@Entity
@Table(
        name = "comment_likes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_comment_like", columnNames = {"comment_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_comment_like_user", columnList = "user_id")
        }
)
public class CommentLike extends PanacheEntity {

    @Column(name = "comment_id", nullable = false)
    public Long commentId;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public static Optional<CommentLike> findByCommentAndUser(Long commentId, UUID userId) {
        return find("commentId = ?1 and userId = ?2", commentId, userId).firstResultOptional();
    }

    /**
     * Bulk lookup for {@code CommentDTO.likedByMe} (anti N+1, cf. Décision K).
     *
     * <p>Returns the set of {@code commentId} values among the given
     * {@code commentIds} for which the caller has a like row. The empty-input
     * guard avoids issuing a malformed {@code IN ()} query on PostgreSQL.
     */
    public static Set<Long> findLikedCommentIdsByUser(Collection<Long> commentIds, UUID userId) {
        if (commentIds == null || commentIds.isEmpty() || userId == null) {
            return Set.of();
        }
        List<CommentLike> rows = list("userId = ?1 and commentId in ?2", userId, commentIds);
        return rows.stream().map(c -> c.commentId).collect(Collectors.toUnmodifiableSet());
    }
}
