package ch.unige.events.shared.kafka.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttendanceCreatedEventTest {

    @Test
    void of_factorySetsAllFieldsAndOccurredAt() {
        UUID user = UUID.randomUUID();
        AttendanceCreatedEvent ev = AttendanceCreatedEvent.of(7L, 42L, user);

        assertEquals(7L, ev.attendanceId());
        assertEquals(42L, ev.eventId());
        assertEquals(user, ev.userId());
        assertNotNull(ev.occurredAt());
    }

    @Test
    void of_acceptsNullUserId() {
        AttendanceCreatedEvent ev = AttendanceCreatedEvent.of(1L, 2L, null);
        assertNull(ev.userId());
    }

    @Test
    void canonicalConstructor_preservesProvidedOccurredAt() {
        Instant fixed = Instant.parse("2026-05-17T12:00:00Z");
        UUID user = UUID.randomUUID();
        AttendanceCreatedEvent ev = new AttendanceCreatedEvent(5L, 9L, user, fixed);
        assertEquals(fixed, ev.occurredAt());
        assertEquals(user, ev.userId());
    }
}
