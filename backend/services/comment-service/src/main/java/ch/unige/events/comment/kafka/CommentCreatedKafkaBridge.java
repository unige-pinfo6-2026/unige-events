package ch.unige.events.comment.kafka;

import ch.unige.events.shared.kafka.events.CommentCreatedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

@ApplicationScoped
public class CommentCreatedKafkaBridge {

    @Inject CommentCreatedPublisher publisher;

    void onAfterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) CommentCreatedEvent ev) {
        publisher.send(ev);
    }
}
