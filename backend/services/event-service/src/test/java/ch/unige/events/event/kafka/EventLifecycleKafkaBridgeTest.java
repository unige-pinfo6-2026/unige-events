package ch.unige.events.event.kafka;

import ch.unige.events.shared.kafka.events.EventLifecycleEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pattern aligned with the 4 sibling bridge tests (CommentCreatedKafkaBridge,
 * CoOrganizerKafkaBridge, FollowLifecycleKafkaBridge, EventBannedKafka
 * Bridge): one observation per discriminated event type.
 */
class EventLifecycleKafkaBridgeTest {

    @Test
    void onAfterCommit_published_callsPublishedOnPublisher() {
        EventLifecyclePublisher publisher = mock(EventLifecyclePublisher.class);
        EventLifecycleKafkaBridge bridge = new EventLifecycleKafkaBridge();
        bridge.publisher = publisher;

        UUID creatorId = UUID.randomUUID();
        bridge.onAfterCommit(EventLifecycleEvent.published(42L, creatorId));

        verify(publisher).published(42L, creatorId);
    }

    @Test
    void onAfterCommit_cancelled_callsCancelledOnPublisher() {
        EventLifecyclePublisher publisher = mock(EventLifecyclePublisher.class);
        EventLifecycleKafkaBridge bridge = new EventLifecycleKafkaBridge();
        bridge.publisher = publisher;

        UUID creatorId = UUID.randomUUID();
        bridge.onAfterCommit(EventLifecycleEvent.cancelled(42L, creatorId));

        verify(publisher).cancelled(42L, creatorId);
    }

    @Test
    void onAfterCommit_expired_callsExpiredOnPublisher() {
        EventLifecyclePublisher publisher = mock(EventLifecyclePublisher.class);
        EventLifecycleKafkaBridge bridge = new EventLifecycleKafkaBridge();
        bridge.publisher = publisher;

        UUID creatorId = UUID.randomUUID();
        bridge.onAfterCommit(EventLifecycleEvent.expired(42L, creatorId));

        verify(publisher).expired(42L, creatorId);
    }
}
