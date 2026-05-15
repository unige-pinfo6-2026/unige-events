package ch.unige.events.report.kafka;

import ch.unige.events.report.outbox.EventBannedOutbox;
import ch.unige.events.shared.kafka.events.EventBannedEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class EventBannedPublisherTest {

    @Inject
    EventBannedPublisher publisher;

    @AfterEach
    @Transactional
    void cleanup() {
        EventBannedOutbox.deleteAll();
    }

    @Test
    @Transactional
    void persist_writesOutboxRowWithSerializedPayload() {
        UUID admin = UUID.randomUUID();
        EventBannedEvent ev = EventBannedEvent.banned(42L, admin, "spam");

        publisher.persist(ev);

        EventBannedOutbox row = EventBannedOutbox.<EventBannedOutbox>findAll().firstResult();
        assertNotNull(row);
        assertEquals(42L, row.eventId);
        assertEquals(admin, row.bannedBy);
        assertEquals(ev.bannedAt(), row.occurredAt);
        assertNotNull(row.payloadJson);
        assertTrue(row.payloadJson.contains("\"reason\":\"spam\""));
        assertNull(row.publishedAt);
        assertEquals(0, row.attempts);
    }

    @Test
    @Transactional
    void persist_nullBannedBy_isAccepted() {
        EventBannedEvent ev = EventBannedEvent.banned(99L, null, "auto");

        publisher.persist(ev);

        EventBannedOutbox row = EventBannedOutbox.<EventBannedOutbox>findAll().firstResult();
        assertNotNull(row);
        assertNull(row.bannedBy);
        assertTrue(row.payloadJson.contains("\"reason\":\"auto\""));
    }
}
