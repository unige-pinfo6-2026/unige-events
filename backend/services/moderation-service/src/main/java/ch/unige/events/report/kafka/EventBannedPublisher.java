package ch.unige.events.report.kafka;

import ch.unige.events.shared.kafka.events.EventBannedEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

/**
 * Producer for {@code events.banned} (Décision F + KAFKA-001/002 BLOCKER).
 * Emitted from moderation-service ; consumed by event-service which applies
 * {@code event.status = BANNED} locally — no more cross-schema mutation.
 */
@ApplicationScoped
public class EventBannedPublisher {

    private final Emitter<EventBannedEvent> emitter;

    @Inject
    public EventBannedPublisher(@Channel("events-banned") Emitter<EventBannedEvent> emitter) {
        this.emitter = emitter;
    }

    public void send(EventBannedEvent ev) {
        try {
            emitter.send(ev);
        } catch (Exception e) {
            Log.warnf(e, "Failed to publish events.banned for event %d ; the ban will not propagate to event-service", ev.eventId());
        }
    }
}
