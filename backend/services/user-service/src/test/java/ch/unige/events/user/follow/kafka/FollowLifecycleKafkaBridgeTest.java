package ch.unige.events.user.follow.kafka;

import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FollowLifecycleKafkaBridgeTest {

    @Test
    void onAfterCommit_delegatesToPublisher() {
        FollowLifecyclePublisher publisher = mock(FollowLifecyclePublisher.class);
        FollowLifecycleKafkaBridge bridge = new FollowLifecycleKafkaBridge();
        bridge.publisher = publisher;

        FollowLifecycleEvent ev = FollowLifecycleEvent.followed(UUID.randomUUID(), UUID.randomUUID());
        bridge.onAfterCommit(ev);

        verify(publisher).send(ev);
    }
}
