package ch.unige.events.notification.kafka;

import ch.unige.events.shared.kafka.events.CommentMentionEvent;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the no-arg public constructor + the inherited
 * {@code deserialize(String, byte[])} happy path on
 * {@link CommentMentionEventDeserializer}. Same shape as
 * {@code CommentCreatedEventDeserializerTest} — Kafka instantiates this
 * class by reflection from {@code application.properties}, so the public
 * no-arg ctor must compile and produce a working deserializer.
 */
@QuarkusTest
class CommentMentionEventDeserializerTest {

    @Test
    void noArgConstructor_isPublicAndUsable() {
        CommentMentionEventDeserializer d = new CommentMentionEventDeserializer();
        assertNotNull(d);
    }

    @Test
    void deserialize_validJson_returnsRecord() {
        UUID author = UUID.randomUUID();
        UUID mentioned = UUID.randomUUID();
        String json = "{\"commentId\":10,\"eventId\":42,"
                + "\"authorId\":\"" + author + "\","
                + "\"mentionedUserId\":\"" + mentioned + "\","
                + "\"authorLabel\":\"Bob Smith\","
                + "\"eventTitle\":\"Concert\","
                + "\"occurredAt\":\"2026-05-19T10:00:00Z\"}";

        try (CommentMentionEventDeserializer d = new CommentMentionEventDeserializer()) {
            CommentMentionEvent ev = d.deserialize("comments.mentions", json.getBytes(StandardCharsets.UTF_8));
            assertNotNull(ev);
            assertEquals(10L, ev.commentId());
            assertEquals(42L, ev.eventId());
            assertEquals(author, ev.authorId());
            assertEquals(mentioned, ev.mentionedUserId());
            assertEquals("Bob Smith", ev.authorLabel());
            assertEquals("Concert", ev.eventTitle());
        }
    }

    @Test
    void deserialize_nullBytes_returnsNull() {
        try (CommentMentionEventDeserializer d = new CommentMentionEventDeserializer()) {
            assertNull(d.deserialize("comments.mentions", null));
        }
    }
}
