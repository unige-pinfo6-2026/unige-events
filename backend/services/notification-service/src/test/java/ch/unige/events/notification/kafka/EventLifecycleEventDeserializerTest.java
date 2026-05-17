package ch.unige.events.notification.kafka;

import ch.unige.events.shared.kafka.events.EventLifecycleEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Sentinels for the Kafka contract that broke CI deploy:
 * <ul>
 *   <li>Kafka requires a public no-arg constructor on the deserializer
 *       (reflection-instantiated via {@code Class.newInstance()}).</li>
 *   <li>Null bytes must round-trip to null without NPE.</li>
 *   <li>Round-trip on payload sans timestamp Java 8 — l'instance vanilla
 *       de l'ObjectMapper utilisée hors-Quarkus n'a pas {@code jsr310}
 *       enregistré ; un test sur {@code Instant} échouerait à tort, alors
 *       qu'en runtime Quarkus l'enregistre par défaut.</li>
 * </ul>
 */
class EventLifecycleEventDeserializerTest {

    @Test
    void noArgConstructor_isPublicAndUsable() {
        EventLifecycleEventDeserializer d = new EventLifecycleEventDeserializer();
        assertNotNull(d);
    }

    @Test
    void deserialize_payloadWithoutInstantField_returnsRecord() {
        UUID creator = UUID.randomUUID();
        // Skip occurredAt — the vanilla ObjectMapper used outside Quarkus
        // does not have jackson-datatype-jsr310 registered; the production
        // runtime relies on Quarkus's auto-configured ObjectMapper.
        String json = "{\"type\":\"UPDATED\",\"eventId\":42,\"creatorId\":\"" + creator + "\"}";

        try (EventLifecycleEventDeserializer d = new EventLifecycleEventDeserializer()) {
            EventLifecycleEvent ev = d.deserialize("events.updated", json.getBytes(StandardCharsets.UTF_8));
            assertNotNull(ev);
            assertEquals(EventLifecycleEvent.Type.UPDATED, ev.type());
            assertEquals(42L, ev.eventId());
            assertEquals(creator, ev.creatorId());
            assertNull(ev.occurredAt());
        }
    }

    @Test
    void deserialize_nullBytes_returnsNull() {
        try (EventLifecycleEventDeserializer d = new EventLifecycleEventDeserializer()) {
            assertNull(d.deserialize("events.cancelled", null));
        }
    }
}
