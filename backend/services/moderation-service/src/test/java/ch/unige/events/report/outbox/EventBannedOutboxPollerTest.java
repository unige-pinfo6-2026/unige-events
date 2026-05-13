package ch.unige.events.report.outbox;

import ch.unige.events.shared.kafka.events.EventBannedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
class EventBannedOutboxPollerTest {

    @Inject
    ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    private final Emitter<EventBannedEvent> emitter = mock(Emitter.class);

    private EventBannedOutboxPoller poller;

    @BeforeEach
    void setup() {
        poller = new EventBannedOutboxPoller(emitter, objectMapper);
    }

    @AfterEach
    @Transactional
    void cleanup() {
        EventBannedOutbox.deleteAll();
    }

    @Test
    @Transactional
    void publishPending_kafkaUp_marksPublishedAt() throws Exception {
        when(emitter.send(any(EventBannedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        Long id = persistRow(42L, UUID.randomUUID(), "spam");

        poller.publishPending();

        EventBannedOutbox refreshed = EventBannedOutbox.findById(id);
        assertNotNull(refreshed.publishedAt);
        assertEquals(0, refreshed.attempts);
        assertNull(refreshed.lastError);
    }

    @Test
    @Transactional
    void publishPending_kafkaDown_incrementsAttemptsAndLogs() throws Exception {
        when(emitter.send(any(EventBannedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        Long id = persistRow(99L, null, "auto");

        poller.publishPending();

        EventBannedOutbox refreshed = EventBannedOutbox.findById(id);
        assertNull(refreshed.publishedAt);
        assertEquals(1, refreshed.attempts);
        assertNotNull(refreshed.lastError);
        assertTrue(refreshed.lastError.contains("kafka down"));
    }

    private Long persistRow(long eventId, UUID bannedBy, String reason) throws Exception {
        EventBannedEvent ev = EventBannedEvent.banned(eventId, bannedBy, reason);
        EventBannedOutbox row = new EventBannedOutbox();
        row.eventId = ev.eventId();
        row.bannedBy = ev.bannedBy();
        row.occurredAt = ev.bannedAt();
        row.payloadJson = objectMapper.writeValueAsString(ev);
        row.attempts = 0;
        row.createdAt = Instant.now();
        row.persist();
        return row.id;
    }
}
