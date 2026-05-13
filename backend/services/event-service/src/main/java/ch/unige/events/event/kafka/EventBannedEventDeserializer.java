package ch.unige.events.event.kafka;

import ch.unige.events.shared.kafka.events.EventBannedEvent;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

public class EventBannedEventDeserializer extends ObjectMapperDeserializer<EventBannedEvent> {
    public EventBannedEventDeserializer() {
        super(EventBannedEvent.class);
    }
}
