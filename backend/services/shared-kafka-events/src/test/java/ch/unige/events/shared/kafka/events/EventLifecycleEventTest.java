package ch.unige.events.shared.kafka.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertTrue(!ev.occurredAt().isBefore(before) && !ev.occurredAt().isAfter(after));
    }

    @Test
    void cancelled_factory() {
        UUID c = UUID.randomUUID();
        EventLifecycleEvent ev = EventLifecycleEvent.cancelled(7L, c);
        assertEquals(EventLifecycleEvent.Type.CANCELLED, ev.type());
        assertEquals(7L, ev.eventId());
    }

    @Test
    void expired_factory() {
        EventLifecycleEvent ev = EventLifecycleEvent.expired(99L, UUID.randomUUID());
        assertEquals(EventLifecycleEvent.Type.EXPIRED, ev.type());
        assertEquals(99L, ev.eventId());
    }

    @Test
    void published_acceptsNullCreatorId() {
        EventLifecycleEvent ev = EventLifecycleEvent.published(1L, null);
        assertNull(ev.creatorId());
    }

    @Test
    void typeEnum_threeValues() {
        assertEquals(3, EventLifecycleEvent.Type.values().length);
        assertEquals(EventLifecycleEvent.Type.PUBLISHED, EventLifecycleEvent.Type.values()[0]);
        assertEquals(EventLifecycleEvent.Type.CANCELLED, EventLifecycleEvent.Type.values()[1]);
        assertEquals(EventLifecycleEvent.Type.EXPIRED, EventLifecycleEvent.Type.values()[2]);
    }
}
