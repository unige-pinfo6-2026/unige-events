package ch.unige.events.engagement.comment.kafka;

import ch.unige.events.shared.kafka.events.CommentMentionEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CommentMentionKafkaBridgeTest {

    @Test
    void onAfterCommit_delegatesToPublisher() {
        CommentMentionPublisher publisher = mock(CommentMentionPublisher.class);
        CommentMentionKafkaBridge bridge = new CommentMentionKafkaBridge();
        bridge.publisher = publisher;

        CommentMentionEvent ev = CommentMentionEvent.of(1L, 7L, UUID.randomUUID(), UUID.randomUUID(),
                "Bob", "Test event");
        bridge.onAfterCommit(ev);

        verify(publisher).send(ev);
    }
}
