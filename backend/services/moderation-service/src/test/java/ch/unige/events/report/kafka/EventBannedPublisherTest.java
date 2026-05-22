package ch.unige.events.report.kafka;

import ch.unige.events.report.outbox.EventBannedOutbox;
import ch.unige.events.shared.kafka.events.EventBannedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void persist_serializationFailure_throwsIllegalState() throws Exception {
        // Drive the JsonProcessingException catch with a mapper that refuses
        // to serialize. Built manually (not the CDI bean) so the failing
        // mapper is isolated to this case; persist() throws before the row
        // is written, so no transaction/DB is involved.
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

        EventBannedPublisher isolated = new EventBannedPublisher();
        isolated.objectMapper = failing;

        EventBannedEvent ev = EventBannedEvent.banned(7L, UUID.randomUUID(), "spam");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> isolated.persist(ev));
        assertTrue(ex.getMessage().contains("Failed to serialize"));
    }
}
