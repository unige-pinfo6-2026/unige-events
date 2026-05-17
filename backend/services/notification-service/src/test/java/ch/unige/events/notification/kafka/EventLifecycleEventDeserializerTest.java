package ch.unige.events.notification.kafka;

import ch.unige.events.shared.kafka.events.EventLifecycleEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventLifecycleEventDeserializerTest {

    @Test
    void noArgConstructor_isPublicAndUsable() {
        // Kafka requires reflection-instantiable deserializers — exercise the
        // contract that broke the deploy when we tried to plug
        // ObjectMapperDeserializer directly.
        EventLifecycleEventDeserializer d = new EventLifecycleEventDeserializer();
        assertNotNull(d);
    }

    @Test
    void deserialize_validJson_returnsRecord() {
        UUID creator = UUID.randomUUID();
        String json = "{\"type\":\"CANCELLED\",\"eventId\":42,\"creatorId\":\""
                + creator + "\",\"occurredAt\":\"2026-05-17T12:00:00Z\"}";

        try (EventLifecycleEventDeserializer d = new EventLifecycleEventDeserializer()) {
            EventLifecycleEvent ev = d.deserialize("events.cancelled", json.getBytes(StandardCharsets.UTF_8));
            assertNotNull(ev);
            assertEquals(EventLifecycleEvent.Type.CANCELLED, ev.type());
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
