package ch.unige.events.report.kafka;

import ch.unige.events.shared.kafka.events.EventBannedEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EventBannedKafkaBridgeTest {

    /**
     * Decision K (ADR-003): the bridge no longer talks to Kafka directly.
     * It must hand the event to the publisher, which writes a row in the
     * transactional outbox. The bridge holds no {@code Emitter} dependency
     * by construction, so a direct send is impossible.
     */
    @Test
    void onBanned_persistsOutboxRow_notDirectEmitter() {
        EventBannedPublisher publisher = mock(EventBannedPublisher.class);
        EventBannedKafkaBridge bridge = new EventBannedKafkaBridge();
        bridge.publisher = publisher;

        EventBannedEvent ev = EventBannedEvent.banned(42L, UUID.randomUUID(), "spam");
        bridge.onBanned(ev);

        verify(publisher).persist(ev);
    }
}
