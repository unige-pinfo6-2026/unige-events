package ch.unige.events.notification.kafka;

import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Sentinels for the Kafka deserializer used by the 3 follow incoming
 * channels. Mirrors {@link EventLifecycleEventDeserializerTest}:
 * <ul>
 *   <li>Public no-arg constructor (Kafka instantiates via reflection).</li>
 *   <li>JSON payload round-trips to the record.</li>
 *   <li>Null bytes return null without NPE.</li>
 * </ul>
 */
@QuarkusTest
class FollowLifecycleEventDeserializerTest {

    @Test
    void noArgConstructor_isPublicAndUsable() {
        FollowLifecycleEventDeserializer d = new FollowLifecycleEventDeserializer();
        assertNotNull(d);
    }

    @Test
    void deserialize_validJson_returnsRecord() {
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        String json = "{\"type\":\"FOLLOWED\",\"followerId\":\"" + follower
                + "\",\"followedId\":\"" + followed + "\"}";

        try (FollowLifecycleEventDeserializer d = new FollowLifecycleEventDeserializer()) {
            FollowLifecycleEvent ev = d.deserialize("users.followed",
                    json.getBytes(StandardCharsets.UTF_8));
            assertNotNull(ev);
            assertEquals(FollowLifecycleEvent.Type.FOLLOWED, ev.type());
            assertEquals(follower, ev.followerId());
            assertEquals(followed, ev.followedId());
        }
    }

    @Test
    void deserialize_nullBytes_returnsNull() {
        try (FollowLifecycleEventDeserializer d = new FollowLifecycleEventDeserializer()) {
            assertNull(d.deserialize("users.follow-accepted", null));
        }
    }
}
