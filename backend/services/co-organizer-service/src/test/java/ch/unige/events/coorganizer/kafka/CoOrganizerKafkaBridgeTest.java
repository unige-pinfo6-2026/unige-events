package ch.unige.events.coorganizer.kafka;

import ch.unige.events.shared.kafka.events.CoOrganizerEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CoOrganizerKafkaBridgeTest {

    @Test
    void onAfterCommit_delegatesToPublisher() {
        CoOrganizerPublisher publisher = mock(CoOrganizerPublisher.class);
        CoOrganizerKafkaBridge bridge = new CoOrganizerKafkaBridge();
        bridge.publisher = publisher;
        CoOrganizerEvent ev = CoOrganizerEvent.invited(42L, UUID.randomUUID());
        bridge.onAfterCommit(ev);
        verify(publisher).send(ev);
    }
}
