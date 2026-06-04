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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class EventBannedConsumerTest {

    @Inject EventBannedConsumer consumer;
    @Inject EntityManager em;

    private Event create(EventStatus status) {
        Event e = new Event();
        e.title = "T";
        e.description = "d";
        e.location = "l";
        e.startDate = LocalDateTime.of(2999, 1, 1, 0, 0).plusDays(1);
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

        consumer.onBanned(new EventBannedEvent(e.id, UUID.randomUUID(), "spam", Instant.parse("2025-01-01T12:00:00Z")));
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

        consumer.onBanned(new EventBannedEvent(e.id, UUID.randomUUID(), "spam", Instant.parse("2025-01-01T12:00:00Z")));
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
            consumer.onBanned(new EventBannedEvent(99999L, UUID.randomUUID(), "spam", Instant.parse("2025-01-01T12:00:00Z"))));
    }

    @Test
    @TestTransaction
    void onBanned_unknownEvent_logsWarnWithMOD_BAN_LOST() {
        Logger jul = Logger.getLogger(EventBannedConsumer.class.getName());
        Level originalLevel = jul.getLevel();
        List<LogRecord> captured = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) { captured.add(r); }
            @Override public void flush() {}
            @Override public void close() {}
        };
        jul.addHandler(handler);
        jul.setLevel(Level.ALL);
        try {
            consumer.onBanned(new EventBannedEvent(88888L, UUID.randomUUID(), "spam", Instant.parse("2025-01-01T12:00:00Z")));

            assertTrue(
                captured.stream().anyMatch(r -> {
                    String msg = r.getMessage();
                    return msg != null && msg.contains("[MOD_BAN_LOST]") && r.getLevel().intValue() >= Level.WARNING.intValue();
                }),
                "expected a WARN log record containing [MOD_BAN_LOST] but captured=" + captured);
        } finally {
            jul.removeHandler(handler);
            jul.setLevel(originalLevel);
        }
    }
}
