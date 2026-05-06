package ch.unige.events.entity;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class EventTest {

    @Test
    void prePersist_setsTimestamps() {
        Event event = new Event();
        assertNull(event.createdAt);
        assertNull(event.updatedAt);

        event.prePersist();

        assertNotNull(event.createdAt);
        assertNotNull(event.updatedAt);
    }

    @Test
    void preUpdate_updatesTimestamp() {
        Event event = new Event();
        event.prePersist();
        var before = event.updatedAt;

        event.preUpdate();

        assertNotNull(event.updatedAt);
        assertFalse(event.updatedAt.isBefore(before));
    }

    @Test
    void defaultStatus_isDraft() {
        Event event = new Event();
        assertEquals(EventStatus.DRAFT, event.status);
    }

    @Test
    void featured_defaultIsFalse() {
        Event event = new Event();
        assertFalse(event.featured);
    }

    @Test
    void featuredAt_defaultIsNull() {
        Event event = new Event();
        assertNull(event.featuredAt);
    }
}
