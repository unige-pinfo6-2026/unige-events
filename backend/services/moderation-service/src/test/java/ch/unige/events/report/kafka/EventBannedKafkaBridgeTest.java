package ch.unige.events.report.kafka;

import ch.unige.events.shared.kafka.events.EventBannedEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EventBannedKafkaBridgeTest {

    @Test
    void onAfterCommit_delegates() {
        EventBannedPublisher p = mock(EventBannedPublisher.class);
        EventBannedKafkaBridge b = new EventBannedKafkaBridge();
        b.publisher = p;
        EventBannedEvent ev = EventBannedEvent.banned(42L, UUID.randomUUID(), "spam");
        b.onAfterCommit(ev);
        verify(p).send(ev);
    }
}
