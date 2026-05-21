package ch.unige.events.notification.kafka;

import ch.unige.events.shared.kafka.events.CommentMentionEvent;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/**
 * Concrete Kafka deserializer for {@link CommentMentionEvent}. Required
 * because {@link ObjectMapperDeserializer} has no no-arg constructor and
 * smallrye-kafka instantiates the deserializer reflectively from the
 * channel config.
 *
 * <p>Mirrors {@code FollowLifecycleEventDeserializer}.
 */
public class CommentMentionEventDeserializer extends ObjectMapperDeserializer<CommentMentionEvent> {
    public CommentMentionEventDeserializer() {
        super(CommentMentionEvent.class);
    }
}
