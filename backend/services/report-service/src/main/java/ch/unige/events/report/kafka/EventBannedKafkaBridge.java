package ch.unige.events.report.kafka;

import ch.unige.events.shared.kafka.events.EventBannedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

@ApplicationScoped
public class EventBannedKafkaBridge {

    @Inject EventBannedPublisher publisher;

    void onAfterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) EventBannedEvent ev) {
        publisher.send(ev);
    }
}
