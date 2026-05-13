package ch.unige.events.engagement.comment.kafka;

import ch.unige.events.shared.kafka.events.CommentCreatedEvent;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CommentCreatedPublisherTest {

    @SuppressWarnings("unchecked")
    private final Emitter<CommentCreatedEvent> emitter = mock(Emitter.class);

    @Test
    void send_delegatesToEmitter() {
        CommentCreatedPublisher publisher = new CommentCreatedPublisher(emitter);
        CommentCreatedEvent ev = CommentCreatedEvent.created(1L, 7L, UUID.randomUUID(), null);
        publisher.send(ev);
        verify(emitter).send(ev);
    }

    @Test
    void send_emitterFails_swallows() {
        CommentCreatedPublisher publisher = new CommentCreatedPublisher(emitter);
        doThrow(new RuntimeException("kafka down")).when(emitter).send(org.mockito.ArgumentMatchers.any(CommentCreatedEvent.class));
        CommentCreatedEvent ev = CommentCreatedEvent.created(1L, 7L, UUID.randomUUID(), null);
        assertDoesNotThrow(() -> publisher.send(ev));
    }
}
