package ch.unige.events.notification.kafka;

import ch.unige.events.shared.kafka.events.CommentCreatedEvent;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/**
 * Concrete Kafka deserializer for {@link CommentCreatedEvent}. Same
 * rationale as {@link AttendanceCreatedEventDeserializer} — Kafka requires
 * a public no-arg constructor on the deserializer class, which the
 * generic {@code ObjectMapperDeserializer<T>} cannot provide on its own.
 */
public class CommentCreatedEventDeserializer extends ObjectMapperDeserializer<CommentCreatedEvent> {
    public CommentCreatedEventDeserializer() {
        super(CommentCreatedEvent.class);
    }
}
