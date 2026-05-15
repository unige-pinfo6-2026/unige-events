package ch.unige.events.shared.kafka.events;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FollowLifecycleEventTest {

    @Test
    void followed_factory() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        FollowLifecycleEvent ev = FollowLifecycleEvent.followed(a, b);
        assertEquals(FollowLifecycleEvent.Type.FOLLOWED, ev.type());
        assertEquals(a, ev.followerId());
        assertEquals(b, ev.followedId());
        assertNotNull(ev.occurredAt());
    }

    @Test
    void followRequested_factory() {
        FollowLifecycleEvent ev = FollowLifecycleEvent.followRequested(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(FollowLifecycleEvent.Type.REQUESTED, ev.type());
    }

    @Test
    void followAccepted_factory() {
        FollowLifecycleEvent ev = FollowLifecycleEvent.followAccepted(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(FollowLifecycleEvent.Type.ACCEPTED, ev.type());
    }

    @Test
    void typeEnum_threeValuesOrdered() {
        assertEquals(3, FollowLifecycleEvent.Type.values().length);
        assertEquals(FollowLifecycleEvent.Type.FOLLOWED, FollowLifecycleEvent.Type.values()[0]);
        assertEquals(FollowLifecycleEvent.Type.REQUESTED, FollowLifecycleEvent.Type.values()[1]);
        assertEquals(FollowLifecycleEvent.Type.ACCEPTED, FollowLifecycleEvent.Type.values()[2]);
    }
}
