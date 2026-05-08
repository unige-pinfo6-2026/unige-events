package ch.unige.events.dto.comment;

import ch.unige.events.entity.Comment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound DTO for comments (SCRUM-139).
 *
 * <p>Two factories:
 * <ul>
 *   <li>{@link #from(Comment, boolean)} — projects a single comment (replies always {@code List.of()}).
 *       Used by {@code POST /events/{id}/comments} (a freshly created comment has no replies)
 *       and to build the inner reply DTOs.</li>
 *   <li>{@link #fromTopLevelWithReplies(Comment, List, boolean, Map)} — projects a top-level
 *       comment with its already-batch-loaded replies imbriquées dans {@code replies[]}.
 *       Used by {@code GET /events/{id}/comments}.</li>
 * </ul>
 *
 * <p>{@code likedByMe} is always {@code false} en S6 — sera enrichi par SCRUM-144 (S7) quand
 * l'entité {@code CommentLike} existera.
 */
public record CommentDTO(
        Long id,
        String content,
        UUID authorId,
        String authorDisplayName,
        String authorAvatarUrl,
        boolean authorIsOrganizer,
        int likeCount,
        boolean likedByMe,
        LocalDateTime createdAt,
        Long parentCommentId,
        List<CommentDTO> replies
) {

    public static CommentDTO from(Comment c, boolean authorIsOrganizer) {
        return new CommentDTO(
                c.id,
                c.content,
                c.author != null ? c.author.id : null,
                c.author != null ? c.author.displayName : null,
                c.author != null ? c.author.avatarUrl : null,
                authorIsOrganizer,
                c.likeCount,
                false,
                c.createdAt,
                c.parentComment != null ? c.parentComment.id : null,
                List.of()
        );
    }

    public static CommentDTO fromTopLevelWithReplies(
            Comment top,
            List<Comment> replies,
            boolean topAuthorIsOrganizer,
            Map<UUID, Boolean> repliesAuthorIsOrganizer
    ) {
        List<CommentDTO> replyDTOs = replies.stream()
                .map(r -> CommentDTO.from(
                        r,
                        r.author != null
                                && repliesAuthorIsOrganizer.getOrDefault(r.author.id, false)))
                .toList();
        return new CommentDTO(
                top.id,
                top.content,
                top.author != null ? top.author.id : null,
                top.author != null ? top.author.displayName : null,
                top.author != null ? top.author.avatarUrl : null,
                topAuthorIsOrganizer,
                top.likeCount,
                false,
                top.createdAt,
                null,
                replyDTOs
        );
    }
}
