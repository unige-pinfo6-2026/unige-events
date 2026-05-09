package ch.unige.events.follow.kafka;

import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FollowLifecyclePublisherTest {

    @SuppressWarnings("unchecked")
    private final Emitter<FollowLifecycleEvent> followedEmitter = mock(Emitter.class);
    @SuppressWarnings("unchecked")
    private final Emitter<FollowLifecycleEvent> requestedEmitter = mock(Emitter.class);
    @SuppressWarnings("unchecked")
    private final Emitter<FollowLifecycleEvent> acceptedEmitter = mock(Emitter.class);

    private FollowLifecyclePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new FollowLifecyclePublisher(followedEmitter, requestedEmitter, acceptedEmitter);
    }

    @Test
    void send_followed_routesToFollowedEmitter() {
        FollowLifecycleEvent ev = FollowLifecycleEvent.followed(UUID.randomUUID(), UUID.randomUUID());
        publisher.send(ev);

        ArgumentCaptor<FollowLifecycleEvent> captor = ArgumentCaptor.forClass(FollowLifecycleEvent.class);
        verify(followedEmitter).send(captor.capture());
        verify(requestedEmitter, never()).send(captor.capture());
        verify(acceptedEmitter, never()).send(captor.capture());
        assertEquals(FollowLifecycleEvent.Type.FOLLOWED, captor.getValue().type());
    }

    @Test
    void send_requested_routesToRequestedEmitter() {
        FollowLifecycleEvent ev = FollowLifecycleEvent.followRequested(UUID.randomUUID(), UUID.randomUUID());
        publisher.send(ev);
        verify(requestedEmitter).send(ev);
        verify(followedEmitter, never()).send(ev);
        verify(acceptedEmitter, never()).send(ev);
    }

    @Test
    void send_accepted_routesToAcceptedEmitter() {
        FollowLifecycleEvent ev = FollowLifecycleEvent.followAccepted(UUID.randomUUID(), UUID.randomUUID());
        publisher.send(ev);
        verify(acceptedEmitter).send(ev);
    }

    @Test
    void send_emitterFails_swallowsAndLogs() {
        doThrow(new RuntimeException("kafka down")).when(followedEmitter).send(org.mockito.ArgumentMatchers.any(FollowLifecycleEvent.class));
        FollowLifecycleEvent ev = FollowLifecycleEvent.followed(UUID.randomUUID(), UUID.randomUUID());
        // Must NOT propagate — Kafka outage shouldn't fail the user-facing follow.
        assertDoesNotThrow(() -> publisher.send(ev));
    }
}
