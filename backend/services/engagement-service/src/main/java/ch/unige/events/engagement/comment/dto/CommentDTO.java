package ch.unige.events.engagement.comment.dto;

import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mirror of the legacy CommentDTO. {@code likedByMe} is populated since
 * SCRUM-144 (phase 2 S9) via a single batch query JPQL on {@code comment_likes}
 * (anti N+1, cf. Décision K). Always {@code false} for anonymous callers.
 *
 * <p>The {@code authorDisplayName} / {@code authorAvatarUrl} /
 * {@code authorUsername} fields are populated from a {@link
 * UserPublicResponse} fetched cross-service, not from a local
 * {@code UserStub} navigation.
 *
 * <p>SCRUM-169 — {@code authorUsername} added (nullable) so
 * {@code CommentItem} on the frontend renders {@code @username} as the
 * fallback label when {@code authorDisplayName} is absent, before falling
 * back to the UUID prefix.
 */
public record CommentDTO(
        Long id,
        String content,
        UUID authorId,
        String authorDisplayName,
        String authorAvatarUrl,
        String authorUsername,
        boolean authorIsOrganizer,
        int likeCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        Long parentCommentId,
        List<CommentDTO> replies
) {

    /**
     * Factory utilisée par {@code CommentService.post} pour le 201 retour —
     * {@code likedByMe = false} (un commentaire fraîchement posté n'a pas de
     * like du caller).
     */
    public static CommentDTO from(Comment c, UserPublicResponse author, boolean authorIsOrganizer) {
        return from(c, author, authorIsOrganizer, false);
    }

    /**
     * Factory complète avec {@code likedByMe} explicite, utilisée par
     * {@code CommentService.getByEvent} après la résolution batch de
     * {@code CommentLike.findLikedCommentIdsByUser} (anti N+1).
     */
    public static CommentDTO from(Comment c, UserPublicResponse author, boolean authorIsOrganizer,
                                   boolean likedByMe) {
        return new CommentDTO(
                c.id,
                c.content,
                c.authorId,
                author != null ? author.displayName() : null,
                author != null ? author.avatarUrl() : null,
                author != null ? author.username() : null,
                authorIsOrganizer,
                c.likeCount,
                likedByMe,
                c.createdAt,
                c.parentComment != null ? c.parentComment.id : null,
                List.of()
        );
    }

    /**
     * Backward-compatible factory (pre-SCRUM-144) — replies always non-liked.
     * Use {@link #fromTopLevelWithRepliesAndLikes(Comment, UserPublicResponse, List,
     * Map, boolean, Map, Set)} for the SCRUM-144 enriched flow.
     */
    public static CommentDTO fromTopLevelWithReplies(
            Comment top,
            UserPublicResponse topAuthor,
            List<Comment> replies,
            Map<UUID, UserPublicResponse> repliesAuthors,
            boolean topAuthorIsOrganizer,
            Map<UUID, Boolean> repliesAuthorIsOrganizer
    ) {
        return fromTopLevelWithRepliesAndLikes(top, topAuthor, replies, repliesAuthors,
                topAuthorIsOrganizer, repliesAuthorIsOrganizer, Set.of());
    }

    /**
     * SCRUM-144 factory — accepte le {@code Set<Long> likedIds} batch-fetched
     * pour {@code CommentDTO.likedByMe} sans N+1. Pour les callers anonymes,
     * passer {@code Set.of()} (likedByMe sera {@code false} partout).
     */
    public static CommentDTO fromTopLevelWithRepliesAndLikes(
            Comment top,
            UserPublicResponse topAuthor,
            List<Comment> replies,
            Map<UUID, UserPublicResponse> repliesAuthors,
            boolean topAuthorIsOrganizer,
            Map<UUID, Boolean> repliesAuthorIsOrganizer,
            Set<Long> likedIds
    ) {
        List<CommentDTO> replyDTOs = replies.stream()
                .map(r -> CommentDTO.from(
                        r,
                        repliesAuthors.get(r.authorId),
                        repliesAuthorIsOrganizer.getOrDefault(r.authorId, false),
                        likedIds.contains(r.id)))
                .toList();
        return new CommentDTO(
                top.id,
                top.content,
                top.authorId,
                topAuthor != null ? topAuthor.displayName() : null,
                topAuthor != null ? topAuthor.avatarUrl() : null,
                topAuthor != null ? topAuthor.username() : null,
                topAuthorIsOrganizer,
                top.likeCount,
                likedIds.contains(top.id),
                top.createdAt,
                null,
                replyDTOs
        );
    }
}
