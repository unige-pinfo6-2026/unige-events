package ch.unige.events.coorganizer.kafka;

import ch.unige.events.shared.kafka.events.CoOrganizerEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

@ApplicationScoped
public class CoOrganizerKafkaBridge {

    @Inject CoOrganizerPublisher publisher;

    void onAfterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) CoOrganizerEvent ev) {
        publisher.send(ev);
    }
}
