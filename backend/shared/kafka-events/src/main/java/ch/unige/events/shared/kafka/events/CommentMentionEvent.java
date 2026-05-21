package ch.unige.events.shared.kafka.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of the {@code comments.mentions} Kafka topic — one event per
 * mentioned user (1 comment with 3 {@code @handles} → 3 events). Mirrors
 * the follow-lifecycle pattern : the producer (engagement-service)
 * resolves {@code @<handle>} → UUID before publishing so the consumer
 * (notification-service) is trivial — no parsing, no user-service hop.
 *
 * <p>The {@code authorLabel} is pre-computed by the producer
 * (displayName → @username → "Un utilisateur") so the consumer can
 * format the message template without any REST callback. Same with
 * {@code eventTitle}.
 *
 * <p>Self-mentions are filtered upstream by the producer (locked-in #11) ;
 * the consumer trusts that {@code mentionedUserId != authorId}.
 *
 * <p>Partition key = {@code mentionedUserId} so all mentions for a given
 * user land on the same partition (preserves notification order per
 * recipient).
 */
public record CommentMentionEvent(
        long commentId,
        long eventId,
        UUID authorId,
        UUID mentionedUserId,
        String authorLabel,
        String eventTitle,
        Instant occurredAt
) {

    public static CommentMentionEvent of(long commentId, long eventId, UUID authorId,
                                         UUID mentionedUserId, String authorLabel,
                                         String eventTitle) {
        return new CommentMentionEvent(commentId, eventId, authorId, mentionedUserId,
                authorLabel, eventTitle, Instant.now());
    }
}
