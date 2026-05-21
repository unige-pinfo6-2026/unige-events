package ch.unige.events.engagement.comment.kafka;

import ch.unige.events.shared.kafka.events.CommentMentionEvent;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * {@code @QuarkusTest} so quarkus-jacoco sees the class via the
 * QuarkusClassLoader and records its coverage — plain JUnit tests in
 * this codebase are otherwise invisible to the coverage report (cf. the
 * fix we already had to apply on other consumer tests). The test itself
 * is a pure unit test ; the annotation is purely instrumentation glue.
 */
@QuarkusTest
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
