package ch.unige.events.engagement.comment.kafka;

import ch.unige.events.shared.kafka.events.CommentMentionEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Producer for {@code comments.mentions} — one Kafka message per mentioned
 * user. Mirrors {@code FollowLifecyclePublisher} from user-service.
 *
 * <p>Failures swallowed (post-commit, no rollback possible) ; an error log
 * is the only operator signal.
 */
@ApplicationScoped
public class CommentMentionPublisher {

    private final Emitter<CommentMentionEvent> emitter;

    @Inject
    public CommentMentionPublisher(@Channel("comments-mentions-out") Emitter<CommentMentionEvent> emitter) {
        this.emitter = emitter;
    }

    public void send(CommentMentionEvent ev) {
        try {
            emitter.send(ev);
        } catch (RuntimeException e) {
            Log.errorf(e,
                    "[KAFKA_PUBLISH_FAIL_comments_mentions] Failed to publish mention for comment=%d mentionedUser=%s — notification will be missed",
                    ev.commentId(), ev.mentionedUserId());
        }
    }
}
