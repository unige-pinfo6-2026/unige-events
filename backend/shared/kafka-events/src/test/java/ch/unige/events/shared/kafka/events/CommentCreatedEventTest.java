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
        CommentCreatedEvent ev = CommentCreatedEvent.created(10L, 7L, author, null,
                "Hello @bob.smith", "Concert d'ouverture");
        assertEquals(10L, ev.commentId());
        assertEquals(7L, ev.eventId());
        assertEquals(author, ev.authorId());
        assertNull(ev.parentCommentId());
        assertNotNull(ev.createdAt());
        assertEquals("Hello @bob.smith", ev.content());
        assertEquals("Concert d'ouverture", ev.eventTitle());
    }

    @Test
    void created_reply() {
        CommentCreatedEvent ev = CommentCreatedEvent.created(11L, 7L, UUID.randomUUID(), 10L,
                "Re: @alice.dosh", "Concert d'ouverture");
        assertEquals(10L, ev.parentCommentId());
        assertEquals("Re: @alice.dosh", ev.content());
        assertEquals("Concert d'ouverture", ev.eventTitle());
    }

    @Test
    void created_acceptsNullContentAndTitle() {
        // Defensive — Jackson tolerates null on additive fields, and a legacy
        // producer that hasn't been upgraded would emit null/null. The
        // consumer side is expected to short-circuit gracefully.
        CommentCreatedEvent ev = CommentCreatedEvent.created(12L, 7L, UUID.randomUUID(), null,
                null, null);
        assertNull(ev.content());
        assertNull(ev.eventTitle());
    }
}
