package ch.unige.events.engagement.comment.dto;

import ch.unige.events.engagement.comment.entity.Comment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirror of the legacy CommentDTO. {@code likedByMe} stays {@code false}
 * in S6/S8 — wired by SCRUM-144 at S7+ when {@code CommentLike} ships.
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
