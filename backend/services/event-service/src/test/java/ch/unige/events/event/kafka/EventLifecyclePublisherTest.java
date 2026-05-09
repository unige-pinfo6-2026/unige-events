package ch.unige.events.event.kafka;

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

class EventLifecyclePublisherTest {

    @SuppressWarnings("unchecked")
    private final Emitter<EventLifecycleEvent> publishedEmitter = mock(Emitter.class);
    @SuppressWarnings("unchecked")
    private final Emitter<EventLifecycleEvent> cancelledEmitter = mock(Emitter.class);
    @SuppressWarnings("unchecked")
    private final Emitter<EventLifecycleEvent> expiredEmitter = mock(Emitter.class);

    private EventLifecyclePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new EventLifecyclePublisher(publishedEmitter, cancelledEmitter, expiredEmitter);
    }

    @Test
    void published_routesToPublishedEmitter() {
        UUID creator = UUID.randomUUID();
        publisher.published(42L, creator);

        ArgumentCaptor<EventLifecycleEvent> captor = ArgumentCaptor.forClass(EventLifecycleEvent.class);
        verify(publishedEmitter).send(captor.capture());
        EventLifecycleEvent ev = captor.getValue();
        assertEquals(EventLifecycleEvent.Type.PUBLISHED, ev.type());
        assertEquals(42L, ev.eventId());
        assertEquals(creator, ev.creatorId());

        verify(cancelledEmitter, never()).send(org.mockito.ArgumentMatchers.any(EventLifecycleEvent.class));
        verify(expiredEmitter, never()).send(org.mockito.ArgumentMatchers.any(EventLifecycleEvent.class));
    }

    @Test
    void cancelled_routesToCancelledEmitter() {
        UUID creator = UUID.randomUUID();
        publisher.cancelled(7L, creator);

        ArgumentCaptor<EventLifecycleEvent> captor = ArgumentCaptor.forClass(EventLifecycleEvent.class);
        verify(cancelledEmitter).send(captor.capture());
        assertEquals(EventLifecycleEvent.Type.CANCELLED, captor.getValue().type());
        assertEquals(7L, captor.getValue().eventId());

        verify(publishedEmitter, never()).send(org.mockito.ArgumentMatchers.any(EventLifecycleEvent.class));
        verify(expiredEmitter, never()).send(org.mockito.ArgumentMatchers.any(EventLifecycleEvent.class));
    }

    @Test
    void expired_routesToExpiredEmitter() {
        UUID creator = UUID.randomUUID();
        publisher.expired(99L, creator);

        ArgumentCaptor<EventLifecycleEvent> captor = ArgumentCaptor.forClass(EventLifecycleEvent.class);
        verify(expiredEmitter).send(captor.capture());
        assertEquals(EventLifecycleEvent.Type.EXPIRED, captor.getValue().type());
        assertEquals(99L, captor.getValue().eventId());

        verify(publishedEmitter, never()).send(org.mockito.ArgumentMatchers.any(EventLifecycleEvent.class));
        verify(cancelledEmitter, never()).send(org.mockito.ArgumentMatchers.any(EventLifecycleEvent.class));
    }

    @Test
    void emitFailure_isSwallowedNotPropagated() {
        // Mockito reports unchecked exceptions on Emitter.send(...) the
        // same as if the broker rejected the message — assert the
        // publisher catches them so a Kafka outage doesn't fail the
        // user-facing transaction.
        doThrow(new RuntimeException("broker down"))
                .when(publishedEmitter).send(org.mockito.ArgumentMatchers.any(EventLifecycleEvent.class));

        assertDoesNotThrow(() -> publisher.published(1L, UUID.randomUUID()));
    }

    @Test
    void published_acceptsNullCreatorId() {
        publisher.published(1L, null);
        ArgumentCaptor<EventLifecycleEvent> captor = ArgumentCaptor.forClass(EventLifecycleEvent.class);
        verify(publishedEmitter).send(captor.capture());
        assertEquals(null, captor.getValue().creatorId());
    }
}
