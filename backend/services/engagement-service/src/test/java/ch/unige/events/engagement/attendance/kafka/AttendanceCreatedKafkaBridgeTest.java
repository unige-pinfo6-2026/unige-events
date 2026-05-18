package ch.unige.events.engagement.attendance.kafka;

import ch.unige.events.shared.kafka.events.AttendanceCreatedEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AttendanceCreatedKafkaBridgeTest {

    @Test
    void onAfterCommit_forwardsToPublisher() {
        AttendanceCreatedPublisher publisher = mock(AttendanceCreatedPublisher.class);
        AttendanceCreatedKafkaBridge bridge = new AttendanceCreatedKafkaBridge(publisher);

        AttendanceCreatedEvent ev = AttendanceCreatedEvent.of(7L, 42L, UUID.randomUUID());
        bridge.onAfterCommit(ev);

        verify(publisher).send(ev);
    }
}
