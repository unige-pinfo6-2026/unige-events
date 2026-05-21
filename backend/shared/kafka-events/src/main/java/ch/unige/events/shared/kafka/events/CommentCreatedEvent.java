package ch.unige.events.shared.kafka.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of the {@code comments.created} Kafka topic. Partition
 * key = {@code eventId} so all comments of an event land on the same
 * partition (preserves ordering for a future fan-out projection).
 *
 * <p>{@code parentCommentId} is null for top-level comments, set for
 * replies (1-level depth max — enforced by engagement-service (co-located post-finalization)).
 *
 * <p><strong>SCRUM-145 enrichment</strong> — {@code content} and {@code eventTitle}
 * are projected into the payload so the notification-service consumers
 * ({@code CommentMentionConsumer}, {@code NewCommentConsumer}) can parse
 * mentions and compose user-facing messages without a cross-service REST
 * callback. {@code content} is the raw comment text (≤ 500 chars per
 * SCRUM-146 cap) and {@code eventTitle} is the event's title at the
 * moment of the comment. Jackson tolerates a {@code null} value in either
 * field on the consumer side : the parser short-circuits to an empty Set
 * and the message-template helpers fall back to a generic label, so a
 * legacy producer that hasn't been upgraded yet degrades gracefully.
 */
public record CommentCreatedEvent(
        long commentId,
        long eventId,
        UUID authorId,
        Long parentCommentId,
        Instant createdAt,
        String content,
        String eventTitle
) {

    /**
     * Factory used by the engagement-service producer. Sets {@code createdAt}
     * to {@code Instant.now()} ; callers supply every other field.
     */
    public static CommentCreatedEvent created(long commentId, long eventId, UUID authorId,
                                              Long parentCommentId, String content, String eventTitle) {
        return new CommentCreatedEvent(commentId, eventId, authorId, parentCommentId,
                Instant.now(), content, eventTitle);
    }
}
