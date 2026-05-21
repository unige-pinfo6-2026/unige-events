package ch.unige.events.engagement.comment.kafka;

import ch.unige.events.shared.kafka.events.CommentMentionEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/**
 * Routes CDI-fired {@link CommentMentionEvent}s to Kafka only after the
 * JDBC transaction that persisted the comment commits successfully —
 * same pattern as {@code FollowLifecycleKafkaBridge}. Prevents the
 * "phantom mention" class of bugs where a transaction rolls back but
 * Kafka has already shipped the event downstream.
 */
@ApplicationScoped
public class CommentMentionKafkaBridge {

    @Inject CommentMentionPublisher publisher;

    void onAfterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) CommentMentionEvent ev) {
        publisher.send(ev);
    }
}
