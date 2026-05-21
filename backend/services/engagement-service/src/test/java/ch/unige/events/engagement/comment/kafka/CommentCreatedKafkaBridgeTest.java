package ch.unige.events.engagement.comment.kafka;

import ch.unige.events.shared.kafka.events.CommentCreatedEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CommentCreatedKafkaBridgeTest {

    @Test
    void onAfterCommit_delegatesToPublisher() {
        CommentCreatedPublisher publisher = mock(CommentCreatedPublisher.class);
        CommentCreatedKafkaBridge bridge = new CommentCreatedKafkaBridge();
        bridge.publisher = publisher;

        CommentCreatedEvent ev = CommentCreatedEvent.created(1L, 7L, UUID.randomUUID(), null,
                "hello", "Test event");
        bridge.onAfterCommit(ev);

        verify(publisher).send(ev);
    }
}
