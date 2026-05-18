package ch.unige.events.notification.kafka;

import ch.unige.events.shared.kafka.events.EventLifecycleEvent;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/**
 * Concrete Kafka deserializer for {@link EventLifecycleEvent}. Required
 * because {@code ObjectMapperDeserializer} has no public no-arg constructor —
 * Kafka instantiates the configured deserializer via reflection on
 * {@code newInstance()} (cf. Quarkus Kafka docs). Pattern mirrors
 * {@code event-service.kafka.EventBannedEventDeserializer}.
 *
 * <p>Used by both {@code events-cancelled} and {@code events-updated}
 * incoming channels in {@code application.properties} (the payload shape
 * is the same — {@code EventLifecycleEvent} record with a {@code Type}
 * discriminator).
 */
public class EventLifecycleEventDeserializer extends ObjectMapperDeserializer<EventLifecycleEvent> {
    public EventLifecycleEventDeserializer() {
        super(EventLifecycleEvent.class);
    }
}
