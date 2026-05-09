package ch.unige.events.event.kafka;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLifecycleEventTest {

    @Test
    void published_factorySetsTypeAndOccurredAt() {
        UUID creator = UUID.randomUUID();
        Instant before = Instant.now();
        EventLifecycleEvent ev = EventLifecycleEvent.published(42L, creator);
        Instant after = Instant.now();

        assertEquals(EventLifecycleEvent.Type.PUBLISHED, ev.type());
        assertEquals(42L, ev.eventId());
        assertEquals(creator, ev.creatorId());
        assertNotNull(ev.occurredAt());
        // The factory stamps occurredAt = Instant.now() — must fall in
        // the [before, after] bracket (inclusive both ends).
        assertTrue(!ev.occurredAt().isBefore(before)
                && !ev.occurredAt().isAfter(after),
                "occurredAt should be in [before, after] window");
    }

    @Test
    void cancelled_factorySetsTypeAndOccurredAt() {
        UUID creator = UUID.randomUUID();
        EventLifecycleEvent ev = EventLifecycleEvent.cancelled(7L, creator);
        assertEquals(EventLifecycleEvent.Type.CANCELLED, ev.type());
        assertEquals(7L, ev.eventId());
        assertEquals(creator, ev.creatorId());
    }

    @Test
    void expired_factorySetsTypeAndOccurredAt() {
        UUID creator = UUID.randomUUID();
        EventLifecycleEvent ev = EventLifecycleEvent.expired(99L, creator);
        assertEquals(EventLifecycleEvent.Type.EXPIRED, ev.type());
        assertEquals(99L, ev.eventId());
        assertEquals(creator, ev.creatorId());
    }

    @Test
    void published_acceptsNullCreatorId() {
        // Cancel/publish/expire of a recurring child event whose parent
        // FK points to a deleted creator — payload has null creator.
        EventLifecycleEvent ev = EventLifecycleEvent.published(1L, null);
        assertEquals(EventLifecycleEvent.Type.PUBLISHED, ev.type());
        assertEquals(1L, ev.eventId());
        assertEquals(null, ev.creatorId());
    }

    @Test
    void typeEnum_hasThreeValues() {
        // Sentinel — adding a 4th lifecycle transition (e.g. BANNED)
        // breaks this and forces a deliberate update of the contract.
        assertEquals(3, EventLifecycleEvent.Type.values().length);
    }
}
