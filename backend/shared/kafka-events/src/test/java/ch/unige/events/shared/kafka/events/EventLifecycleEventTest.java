package ch.unige.events.shared.kafka.events;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventLifecycleEventTest {

    @Test
    void published_factorySetsTypeAndOccurredAt() {
        UUID creator = UUID.randomUUID();
        EventLifecycleEvent ev = EventLifecycleEvent.published(42L, creator);

        assertEquals(EventLifecycleEvent.Type.PUBLISHED, ev.type());
        assertEquals(42L, ev.eventId());
        assertEquals(creator, ev.creatorId());
        assertNotNull(ev.occurredAt());
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
    void updated_factory() {
        UUID c = UUID.randomUUID();
        EventLifecycleEvent ev = EventLifecycleEvent.updated(123L, c);

        assertEquals(EventLifecycleEvent.Type.UPDATED, ev.type());
        assertEquals(123L, ev.eventId());
        assertEquals(c, ev.creatorId());
        assertNotNull(ev.occurredAt());
    }

    @Test
    void published_acceptsNullCreatorId() {
        EventLifecycleEvent ev = EventLifecycleEvent.published(1L, null);
        assertNull(ev.creatorId());
    }

    @Test
    void typeEnum_fourValues() {
        assertEquals(4, EventLifecycleEvent.Type.values().length);
        assertEquals(EventLifecycleEvent.Type.PUBLISHED, EventLifecycleEvent.Type.values()[0]);
        assertEquals(EventLifecycleEvent.Type.CANCELLED, EventLifecycleEvent.Type.values()[1]);
        assertEquals(EventLifecycleEvent.Type.EXPIRED, EventLifecycleEvent.Type.values()[2]);
        assertEquals(EventLifecycleEvent.Type.UPDATED, EventLifecycleEvent.Type.values()[3]);
    }
}
