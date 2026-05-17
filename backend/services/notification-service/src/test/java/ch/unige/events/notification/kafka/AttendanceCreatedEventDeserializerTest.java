package ch.unige.events.notification.kafka;

import ch.unige.events.shared.kafka.events.AttendanceCreatedEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttendanceCreatedEventDeserializerTest {

    @Test
    void noArgConstructor_isPublicAndUsable() {
        AttendanceCreatedEventDeserializer d = new AttendanceCreatedEventDeserializer();
        assertNotNull(d);
    }

    @Test
    void deserialize_validJson_returnsRecord() {
        UUID user = UUID.randomUUID();
        String json = "{\"attendanceId\":7,\"eventId\":42,\"userId\":\""
                + user + "\",\"occurredAt\":\"2026-05-17T12:00:00Z\"}";

        try (AttendanceCreatedEventDeserializer d = new AttendanceCreatedEventDeserializer()) {
            AttendanceCreatedEvent ev = d.deserialize("attendances.created", json.getBytes(StandardCharsets.UTF_8));
            assertNotNull(ev);
            assertEquals(7L, ev.attendanceId());
            assertEquals(42L, ev.eventId());
            assertEquals(user, ev.userId());
        }
    }

    @Test
    void deserialize_nullBytes_returnsNull() {
        try (AttendanceCreatedEventDeserializer d = new AttendanceCreatedEventDeserializer()) {
            assertNull(d.deserialize("attendances.created", null));
        }
    }
}
