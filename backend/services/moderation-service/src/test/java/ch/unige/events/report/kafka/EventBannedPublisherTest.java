package ch.unige.events.report.kafka;

import ch.unige.events.shared.kafka.events.EventBannedEvent;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EventBannedPublisherTest {

    @SuppressWarnings("unchecked")
    private final Emitter<EventBannedEvent> emitter = mock(Emitter.class);

    @Test
    void send_delegatesToEmitter() {
        EventBannedPublisher p = new EventBannedPublisher(emitter);
        EventBannedEvent ev = EventBannedEvent.banned(42L, UUID.randomUUID(), "spam");
        p.send(ev);
        verify(emitter).send(ev);
    }

    @Test
    void send_emitterFails_swallows() {
        doThrow(new RuntimeException("kafka down")).when(emitter).send(org.mockito.ArgumentMatchers.any(EventBannedEvent.class));
        EventBannedPublisher p = new EventBannedPublisher(emitter);
        EventBannedEvent ev = EventBannedEvent.banned(42L, null, "auto");
        assertDoesNotThrow(() -> p.send(ev));
    }
}
