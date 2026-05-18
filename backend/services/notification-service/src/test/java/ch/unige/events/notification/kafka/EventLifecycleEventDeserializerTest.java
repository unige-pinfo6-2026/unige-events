package ch.unige.events.notification.kafka;

import ch.unige.events.shared.kafka.events.EventLifecycleEvent;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @QuarkusTest so the Quarkus-configured ObjectMapper (with jsr310
 * registered) handles the {@code Instant} round-trip, and so
 * quarkus-jacoco instruments the deserializer for Sonar coverage.
 *
 * <p>Sentinels for the Kafka contract that broke CI deploy:
 * <ul>
 *   <li>Public no-arg constructor (reflection-instantiated via
 *       {@code Class.newInstance()} by Kafka).</li>
 *   <li>JSON payload round-trips to the record.</li>
 *   <li>Null bytes return null without NPE.</li>
 * </ul>
 */
@QuarkusTest
class EventLifecycleEventDeserializerTest {

    @Test
    void noArgConstructor_isPublicAndUsable() {
        EventLifecycleEventDeserializer d = new EventLifecycleEventDeserializer();
        assertNotNull(d);
    }

    @Test
    void deserialize_validJson_returnsRecord() {
        UUID creator = UUID.randomUUID();
        String json = "{\"type\":\"UPDATED\",\"eventId\":42,\"creatorId\":\"" + creator + "\"}";

        try (EventLifecycleEventDeserializer d = new EventLifecycleEventDeserializer()) {
            EventLifecycleEvent ev = d.deserialize("events.updated", json.getBytes(StandardCharsets.UTF_8));
            assertNotNull(ev);
            assertEquals(EventLifecycleEvent.Type.UPDATED, ev.type());
            assertEquals(42L, ev.eventId());
            assertEquals(creator, ev.creatorId());
        }
    }

    @Test
    void deserialize_nullBytes_returnsNull() {
        try (EventLifecycleEventDeserializer d = new EventLifecycleEventDeserializer()) {
            assertNull(d.deserialize("events.cancelled", null));
        }
    }
}
