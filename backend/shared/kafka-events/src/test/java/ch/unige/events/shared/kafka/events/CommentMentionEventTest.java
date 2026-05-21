package ch.unige.events.shared.kafka.events;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommentMentionEventTest {

    @Test
    void of_factoryPopulatesFields() {
        UUID author = UUID.randomUUID();
        UUID mentioned = UUID.randomUUID();
        CommentMentionEvent ev = CommentMentionEvent.of(
                42L, 7L, author, mentioned, "Bob Smith", "Concert");

        assertEquals(42L, ev.commentId());
        assertEquals(7L, ev.eventId());
        assertEquals(author, ev.authorId());
        assertEquals(mentioned, ev.mentionedUserId());
        assertEquals("Bob Smith", ev.authorLabel());
        assertEquals("Concert", ev.eventTitle());
        assertNotNull(ev.occurredAt(), "factory should stamp occurredAt");
    }

    @Test
    void of_acceptsNullEventTitle() {
        // Legacy producers may omit eventTitle ; the consumer falls back to
        // a generic label so passing null at this layer is intentional.
        CommentMentionEvent ev = CommentMentionEvent.of(
                1L, 2L, UUID.randomUUID(), UUID.randomUUID(), "X", null);
        assertEquals(null, ev.eventTitle());
    }
}
