package ch.unige.events.notification.kafka;

import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/**
 * Concrete Kafka deserializer for {@link FollowLifecycleEvent}. Required
 * because {@code ObjectMapperDeserializer} has no public no-arg constructor —
 * Kafka instantiates the configured deserializer via reflection on
 * {@code newInstance()} (cf. Quarkus Kafka docs). The {@code value.deserializer.json-class}
 * hint does NOT work (piège #7 — leçon SCRUM-99). Pattern mirrors
 * {@link EventLifecycleEventDeserializer}.
 *
 * <p>Used by the 3 follow incoming channels in {@code application.properties} —
 * {@code users-followed}, {@code users-follow-requested}, {@code users-follow-accepted}
 * (SCRUM-140). The payload shape is the same — {@link FollowLifecycleEvent} record
 * with a {@code Type} discriminator distinguishing FOLLOWED / REQUESTED / ACCEPTED.
 */
public class FollowLifecycleEventDeserializer extends ObjectMapperDeserializer<FollowLifecycleEvent> {
    public FollowLifecycleEventDeserializer() {
        super(FollowLifecycleEvent.class);
    }
}
