package ch.unige.events.shared.kafka.events;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommentCreatedEventTest {

    @Test
    void created_topLevel() {
        UUID author = UUID.randomUUID();
        CommentCreatedEvent ev = CommentCreatedEvent.created(10L, 7L, author, null);
        assertEquals(10L, ev.commentId());
        assertEquals(7L, ev.eventId());
        assertEquals(author, ev.authorId());
        assertNull(ev.parentCommentId());
        assertNotNull(ev.createdAt());
    }

    @Test
    void created_reply() {
        CommentCreatedEvent ev = CommentCreatedEvent.created(11L, 7L, UUID.randomUUID(), 10L);
        assertEquals(10L, ev.parentCommentId());
    }
}
