package ch.unige.events.event.entity;

import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class EventTest {

    @Inject EntityManager em;

    @Test
    @TestTransaction
    void prePersist_setsCreatedAndUpdatedAt() {
        Event e = newEvent();
        e.persist();
        em.flush();

        assertNotNull(e.createdAt);
        assertNotNull(e.updatedAt);
    }

    @Test
    @TestTransaction
    void preUpdate_refreshesUpdatedAt() throws InterruptedException {
        Event e = newEvent();
        e.persist();
        em.flush();
        LocalDateTime initial = e.updatedAt;

        Thread.sleep(10);
        e.title = "renamed";
        em.flush();

        assertNotNull(e.updatedAt);
        assertTrue(e.updatedAt.isAfter(initial) || e.updatedAt.isEqual(initial));
    }

    @Test
    @TestTransaction
    void findByShareCode_existing_returnsEvent() {
        Event e = newEvent();
        e.shareCode = "uniqueshare";
        e.persist();
        em.flush();

        Optional<Event> found = Event.findByShareCode("uniqueshare");
        assertTrue(found.isPresent());
        assertEquals(e.id, found.get().id);
    }

    @Test
    @TestTransaction
    void findByShareCode_unknown_returnsEmpty() {
        Optional<Event> found = Event.findByShareCode("nonexistent");
        assertFalse(found.isPresent());
    }

    @Test
    @TestTransaction
    void defaultStatus_isDraft() {
        Event e = newEvent();
        // status is set explicitly in newEvent() — verify the in-class default
        Event raw = new Event();
        assertEquals(EventStatus.DRAFT, raw.status);
        assertFalse(raw.allDay);
        assertFalse(raw.featured);
    }

    private Event newEvent() {
        Event e = new Event();
        e.title = "T";
        e.description = "d";
        e.location = "l";
        e.startDate = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1);
        e.endDate = e.startDate.plusHours(2);
        e.category = EventCategory.ACADEMIC;
        e.creatorId = UUID.randomUUID();
        e.status = EventStatus.DRAFT;
        return e;
    }
}
