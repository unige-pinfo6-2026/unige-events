package ch.unige.events.comment.kafka;

import ch.unige.events.shared.kafka.events.CommentCreatedEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Producer for {@code comments.created} (Décision F).
 * Failures swallowed to avoid rolling back the user's comment commit.
 */
@ApplicationScoped
public class CommentCreatedPublisher {

    private final Emitter<CommentCreatedEvent> emitter;

    @Inject
    public CommentCreatedPublisher(@Channel("comments-created") Emitter<CommentCreatedEvent> emitter) {
        this.emitter = emitter;
    }

    public void send(CommentCreatedEvent ev) {
        try {
            emitter.send(ev);
        } catch (Exception e) {
            Log.warnf(e, "Failed to publish comments.created for comment %d ; downstream consumers will not see it", ev.commentId());
        }
    }
}
