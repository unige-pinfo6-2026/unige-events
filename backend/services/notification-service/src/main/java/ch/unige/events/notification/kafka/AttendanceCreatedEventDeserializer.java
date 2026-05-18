package ch.unige.events.notification.kafka;

import ch.unige.events.shared.kafka.events.AttendanceCreatedEvent;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/**
 * Concrete Kafka deserializer for {@link AttendanceCreatedEvent}. Same
 * rationale as {@link EventLifecycleEventDeserializer} — Kafka requires
 * a public no-arg constructor on the deserializer class.
 */
public class AttendanceCreatedEventDeserializer extends ObjectMapperDeserializer<AttendanceCreatedEvent> {
    public AttendanceCreatedEventDeserializer() {
        super(AttendanceCreatedEvent.class);
    }
}
