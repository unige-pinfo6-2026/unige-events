package ch.unige.events.notification.kafka;

import ch.unige.events.shared.kafka.events.AttendanceCreatedEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Same Kafka-contract sentinels as
 * {@link EventLifecycleEventDeserializerTest}. The Instant field is
 * omitted from the round-trip payload — the vanilla ObjectMapper outside
 * Quarkus runtime has no jsr310 module registered.
 */
class AttendanceCreatedEventDeserializerTest {

    @Test
    void noArgConstructor_isPublicAndUsable() {
        AttendanceCreatedEventDeserializer d = new AttendanceCreatedEventDeserializer();
        assertNotNull(d);
    }

    @Test
    void deserialize_payloadWithoutInstantField_returnsRecord() {
        UUID user = UUID.randomUUID();
        String json = "{\"attendanceId\":7,\"eventId\":42,\"userId\":\"" + user + "\"}";

        try (AttendanceCreatedEventDeserializer d = new AttendanceCreatedEventDeserializer()) {
            AttendanceCreatedEvent ev = d.deserialize("attendances.created", json.getBytes(StandardCharsets.UTF_8));
            assertNotNull(ev);
            assertEquals(7L, ev.attendanceId());
            assertEquals(42L, ev.eventId());
            assertEquals(user, ev.userId());
            assertNull(ev.occurredAt());
        }
    }

    @Test
    void deserialize_nullBytes_returnsNull() {
        try (AttendanceCreatedEventDeserializer d = new AttendanceCreatedEventDeserializer()) {
            assertNull(d.deserialize("attendances.created", null));
        }
    }
}
