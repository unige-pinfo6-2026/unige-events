package ch.unige.events.engagement.comment.kafka;

import ch.unige.events.shared.kafka.events.CommentMentionEvent;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** {@code @QuarkusTest} for quarkus-jacoco instrumentation, same as the bridge test. */
@QuarkusTest
class CommentMentionPublisherTest {

    @SuppressWarnings("unchecked")
    private final Emitter<CommentMentionEvent> emitter = mock(Emitter.class);

    @Test
    void send_delegatesToEmitter() {
        CommentMentionPublisher publisher = new CommentMentionPublisher(emitter);
        CommentMentionEvent ev = CommentMentionEvent.of(1L, 7L, UUID.randomUUID(), UUID.randomUUID(),
                "Bob", "Test");
        publisher.send(ev);
        verify(emitter).send(ev);
    }

    @Test
    void send_emitterFails_swallows() {
        CommentMentionPublisher publisher = new CommentMentionPublisher(emitter);
        doThrow(new RuntimeException("kafka down")).when(emitter).send(any(CommentMentionEvent.class));
        CommentMentionEvent ev = CommentMentionEvent.of(1L, 7L, UUID.randomUUID(), UUID.randomUUID(),
                "Bob", "Test");
        // Failure must NOT propagate — post-commit, no rollback possible.
        assertDoesNotThrow(() -> publisher.send(ev));
    }
}
