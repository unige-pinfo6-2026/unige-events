package ch.unige.events.event.kafka;

import ch.unige.events.shared.kafka.events.EventBannedEvent;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers the {@link EventBannedEventDeserializer} constructor (L8/L9) that
 * binds the {@link EventBannedEvent} target type into the generic
 * {@code ObjectMapperDeserializer}. A round-trip deserialize of a sample
 * payload confirms the wiring resolves the right type.
 */
@QuarkusTest
class EventBannedEventDeserializerTest {

    @Test
    void constructor_bindsTargetType() {
        EventBannedEventDeserializer deserializer = new EventBannedEventDeserializer();
        assertNotNull(deserializer);
    }

    @Test
    void deserialize_samplePayload_roundTrips() {
        EventBannedEventDeserializer deserializer = new EventBannedEventDeserializer();
        UUID bannedBy = UUID.randomUUID();
        Instant when = Instant.parse("2026-05-23T10:15:30Z");
        String json = "{\"eventId\":42,\"bannedBy\":\"" + bannedBy + "\","
                + "\"reason\":\"spam\",\"bannedAt\":\"" + when + "\"}";

        EventBannedEvent event = deserializer.deserialize(
                "events.banned", json.getBytes(StandardCharsets.UTF_8));

        assertNotNull(event);
        assertEquals(42L, event.eventId());

        deserializer.close();
    }
}
