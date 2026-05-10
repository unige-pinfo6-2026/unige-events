package ch.unige.events.event.kafka;

import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.kafka.events.EventBannedEvent;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class EventBannedConsumerTest {

    @Inject EventBannedConsumer consumer;
    @Inject EntityManager em;

    private Event create(EventStatus status) {
        Event e = new Event();
        e.title = "T";
        e.description = "d";
        e.location = "l";
        e.startDate = LocalDateTime.now().plusDays(1);
        e.endDate = e.startDate.plusHours(2);
        e.category = EventCategory.ACADEMIC;
        e.creatorId = UUID.randomUUID();
        e.status = status;
        e.persist();
        return e;
    }

    @Test
    @TestTransaction
    void onBanned_publishedEvent_setsBanned() {
        Event e = create(EventStatus.PUBLISHED);
        em.flush();

        consumer.onBanned(new EventBannedEvent(e.id, UUID.randomUUID(), "spam", Instant.now()));
        em.flush();
        em.clear();

        Event reloaded = Event.findById(e.id);
        assertEquals(EventStatus.BANNED, reloaded.status);
    }

    @Test
    @TestTransaction
    void onBanned_alreadyBanned_idempotentNoOp() {
        Event e = create(EventStatus.BANNED);
        em.flush();

        consumer.onBanned(new EventBannedEvent(e.id, UUID.randomUUID(), "spam", Instant.now()));
        em.flush();
        em.clear();

        Event reloaded = Event.findById(e.id);
        assertEquals(EventStatus.BANNED, reloaded.status);
    }

    @Test
    @TestTransaction
    void onBanned_unknownEvent_silentlyIgnored() {
        // Event 99999 doesn't exist — consumer should swallow without throwing
        assertDoesNotThrow(() ->
            consumer.onBanned(new EventBannedEvent(99999L, UUID.randomUUID(), "spam", Instant.now())));
    }
}
