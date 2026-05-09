package ch.unige.events.event.kafka;

import ch.unige.events.shared.kafka.events.EventLifecycleEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.UUID;

/**
 * Fans an {@link EventLifecycleEvent} out to one of three Kafka topics
 * depending on its {@link EventLifecycleEvent.Type}. Producer-only — the
 * consumers will live in notification-service (SCRUM-99) and any future
 * downstream that wants to react to event lifecycle transitions.
 *
 * <p>Wiring is configured in {@code application.properties} under
 * {@code mp.messaging.outgoing.events-{published,cancelled,expired}.*}.
 * The {@code %test} profile flips every connector to
 * {@code smallrye-in-memory} so callers can assert against an
 * {@code InMemoryConnector} sink without needing a real broker.
 *
 * <p>Failures are logged and swallowed — a Kafka outage must not roll
 * back the user-facing transaction (the event row in PostgreSQL is the
 * book-of-record ; the topic is just a notification fan-out).
 */
@ApplicationScoped
public class EventLifecyclePublisher {

    private final Emitter<EventLifecycleEvent> publishedEmitter;
    private final Emitter<EventLifecycleEvent> cancelledEmitter;
    private final Emitter<EventLifecycleEvent> expiredEmitter;

    @Inject
    public EventLifecyclePublisher(
            @Channel("events-published") Emitter<EventLifecycleEvent> publishedEmitter,
            @Channel("events-cancelled") Emitter<EventLifecycleEvent> cancelledEmitter,
            @Channel("events-expired") Emitter<EventLifecycleEvent> expiredEmitter) {
        this.publishedEmitter = publishedEmitter;
        this.cancelledEmitter = cancelledEmitter;
        this.expiredEmitter = expiredEmitter;
    }

    public void published(long eventId, UUID creatorId) {
        send(publishedEmitter, EventLifecycleEvent.published(eventId, creatorId));
    }

    public void cancelled(long eventId, UUID creatorId) {
        send(cancelledEmitter, EventLifecycleEvent.cancelled(eventId, creatorId));
    }

    public void expired(long eventId, UUID creatorId) {
        send(expiredEmitter, EventLifecycleEvent.expired(eventId, creatorId));
    }

    private void send(Emitter<EventLifecycleEvent> emitter, EventLifecycleEvent payload) {
        try {
            emitter.send(payload);
        } catch (Exception e) {
            // The DB row is the source of truth ; a Kafka outage must
            // not propagate up to the caller's transaction.
            Log.warnf(e,
                    "Failed to publish %s for event %d ; downstream consumers will not see this transition",
                    payload.type(), payload.eventId());
        }
    }
}
