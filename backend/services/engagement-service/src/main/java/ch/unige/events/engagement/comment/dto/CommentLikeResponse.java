package ch.unige.events.engagement.comment.dto;

/**
 * Response body for {@code POST /api/comments/{id}/like} (SCRUM-144).
 *
 * <p>{@code liked} is always {@code true} after a successful POST — the field
 * is kept in the record for forward symmetry (a future PUT-style toggle could
 * surface {@code liked: false} on un-like). {@code likeCount} is the post-mutation
 * value : the incremented count on a fresh like (201), or the unchanged count
 * on an idempotent re-POST (200).
 */
public record CommentLikeResponse(boolean liked, int likeCount) {

    public static CommentLikeResponse liked(int likeCount) {
        return new CommentLikeResponse(true, likeCount);
    }
}
