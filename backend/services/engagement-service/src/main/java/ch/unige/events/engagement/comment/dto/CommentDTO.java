package ch.unige.events.engagement.comment.dto;

import ch.unige.events.engagement.comment.entity.Comment;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mirror of the legacy CommentDTO. {@code likedByMe} stays {@code false}
 * in S6/S8 — wired by SCRUM-144 at S7+ when {@code CommentLike} ships.
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

    public static CommentDTO from(Comment c, UserPublicResponse author, boolean authorIsOrganizer) {
        return new CommentDTO(
                c.id,
                c.content,
                c.authorId,
                author != null ? author.displayName() : null,
                author != null ? author.avatarUrl() : null,
                author != null ? author.username() : null,
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
            UserPublicResponse topAuthor,
            List<Comment> replies,
            Map<UUID, UserPublicResponse> repliesAuthors,
            boolean topAuthorIsOrganizer,
            Map<UUID, Boolean> repliesAuthorIsOrganizer
    ) {
        List<CommentDTO> replyDTOs = replies.stream()
                .map(r -> CommentDTO.from(
                        r,
                        repliesAuthors.get(r.authorId),
                        repliesAuthorIsOrganizer.getOrDefault(r.authorId, false)))
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
                false,
                top.createdAt,
                null,
                replyDTOs
        );
    }
}
