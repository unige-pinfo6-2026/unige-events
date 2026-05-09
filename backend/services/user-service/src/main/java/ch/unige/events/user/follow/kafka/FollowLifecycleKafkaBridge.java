package ch.unige.events.user.follow.kafka;

import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/**
 * Routes CDI-fired {@link FollowLifecycleEvent}s to Kafka only after
 * the JDBC transaction commits successfully (Décision A — fixes the
 * fantôme-event class of bugs).
 */
@ApplicationScoped
public class FollowLifecycleKafkaBridge {

    @Inject FollowLifecyclePublisher publisher;

    void onAfterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) FollowLifecycleEvent ev) {
        publisher.send(ev);
    }
}
