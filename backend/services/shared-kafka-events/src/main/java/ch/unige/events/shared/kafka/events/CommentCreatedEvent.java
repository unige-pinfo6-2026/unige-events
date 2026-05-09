package ch.unige.events.shared.kafka.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of the {@code comments.created} Kafka topic. Partition
 * key = {@code eventId} so all comments of an event land on the same
 * partition (preserves ordering for a future fan-out projection).
 *
 * <p>{@code parentCommentId} is null for top-level comments, set for
 * replies (1-level depth max — enforced by comment-service).
 */
public record CommentCreatedEvent(
        long commentId,
        long eventId,
        UUID authorId,
        Long parentCommentId,
        Instant createdAt
) {

    public static CommentCreatedEvent created(long commentId, long eventId, UUID authorId, Long parentCommentId) {
        return new CommentCreatedEvent(commentId, eventId, authorId, parentCommentId, Instant.now());
    }
}
