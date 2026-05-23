package ch.unige.events.event.kafka;

import ch.unige.events.shared.kafka.events.EventLifecycleEvent;
import io.quarkus.test.junit.QuarkusTest;
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

/**
 * Annotated {@code @QuarkusTest} so the executed publisher bytecode is
 * captured by quarkus-jacoco — the swallow {@code catch (RuntimeException)}
 * in {@link EventLifecyclePublisher#send} is exercised once per emitter via
 * {@link #emitFailure_perEmitter_isSwallowedNotPropagated}. The publisher
 * is still constructed by hand with mocked emitters (no CDI wiring needed).
 */
@QuarkusTest
class EventLifecyclePublisherTest {

    @SuppressWarnings("unchecked")
    private final Emitter<EventLifecycleEvent> publishedEmitter = mock(Emitter.class);
    @SuppressWarnings("unchecked")
    private final Emitter<EventLifecycleEvent> cancelledEmitter = mock(Emitter.class);
    @SuppressWarnings("unchecked")
    private final Emitter<EventLifecycleEvent> expiredEmitter = mock(Emitter.class);
    @SuppressWarnings("unchecked")
    private final Emitter<EventLifecycleEvent> updatedEmitter = mock(Emitter.class);

    private EventLifecyclePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new EventLifecyclePublisher(publishedEmitter, cancelledEmitter, expiredEmitter, updatedEmitter);
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

    /**
     * Mockito reports unchecked exceptions on {@code Emitter.send(...)} the
     * same as if the broker rejected the message — assert the publisher
     * swallows them on every routing branch so a Kafka outage doesn't fail
     * the user-facing transaction. Parameterised over the 4 emitters so the
     * single shared {@code catch (RuntimeException)} is reached from each
     * fan-out method.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(EventLifecycleEvent.Type.class)
    void emitFailure_perEmitter_isSwallowedNotPropagated(EventLifecycleEvent.Type type) {
        Emitter<EventLifecycleEvent> failing = switch (type) {
            case PUBLISHED -> publishedEmitter;
            case CANCELLED -> cancelledEmitter;
            case EXPIRED -> expiredEmitter;
            case UPDATED -> updatedEmitter;
        };
        doThrow(new RuntimeException("broker down"))
                .when(failing).send(org.mockito.ArgumentMatchers.any(EventLifecycleEvent.class));

        UUID creator = UUID.randomUUID();
        assertDoesNotThrow(() -> {
            switch (type) {
                case PUBLISHED -> publisher.published(1L, creator);
                case CANCELLED -> publisher.cancelled(1L, creator);
                case EXPIRED -> publisher.expired(1L, creator);
                case UPDATED -> publisher.updated(1L, creator);
            }
        });
    }

    @Test
    void published_acceptsNullCreatorId() {
        publisher.published(1L, null);
        ArgumentCaptor<EventLifecycleEvent> captor = ArgumentCaptor.forClass(EventLifecycleEvent.class);
        verify(publishedEmitter).send(captor.capture());
        assertEquals(null, captor.getValue().creatorId());
    }

    @Test
    void updated_routesToUpdatedEmitter() {
        UUID creator = UUID.randomUUID();
        publisher.updated(123L, creator);

        ArgumentCaptor<EventLifecycleEvent> captor = ArgumentCaptor.forClass(EventLifecycleEvent.class);
        verify(updatedEmitter).send(captor.capture());
        assertEquals(EventLifecycleEvent.Type.UPDATED, captor.getValue().type());
        assertEquals(123L, captor.getValue().eventId());
        assertEquals(creator, captor.getValue().creatorId());

        verify(publishedEmitter, never()).send(org.mockito.ArgumentMatchers.any(EventLifecycleEvent.class));
        verify(cancelledEmitter, never()).send(org.mockito.ArgumentMatchers.any(EventLifecycleEvent.class));
        verify(expiredEmitter, never()).send(org.mockito.ArgumentMatchers.any(EventLifecycleEvent.class));
    }
}
