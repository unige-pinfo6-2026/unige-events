package ch.unige.events.shared.kafka.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventBannedEventTest {

    @Test
    void banned_factoryStampsBannedAt() {
        UUID admin = UUID.randomUUID();
        EventBannedEvent ev = EventBannedEvent.banned(42L, admin, "spam");

        assertEquals(42L, ev.eventId());
        assertEquals(admin, ev.bannedBy());
        assertEquals("spam", ev.reason());
        assertNotNull(ev.bannedAt());
    }

    @Test
    void banned_acceptsNullBannedBy() {
        // ModerationCleanupJob auto-ban path has no human admin
        EventBannedEvent ev = EventBannedEvent.banned(1L, null, "auto-cleanup-3-reports");
        assertNull(ev.bannedBy());
        assertEquals("auto-cleanup-3-reports", ev.reason());
    }

    @Test
    void recordEqualityIsValueBased() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        UUID admin = UUID.randomUUID();
        EventBannedEvent a = new EventBannedEvent(1L, admin, "r", now);
        EventBannedEvent b = new EventBannedEvent(1L, admin, "r", now);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
