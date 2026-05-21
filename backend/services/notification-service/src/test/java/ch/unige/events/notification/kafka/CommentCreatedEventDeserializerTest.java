package ch.unige.events.notification.kafka;

import ch.unige.events.shared.kafka.events.CommentCreatedEvent;
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
 * {@link CommentCreatedEventDeserializer}. Same shape as
 * {@code AttendanceCreatedEventDeserializerTest} ; Kafka instantiates
 * this class by reflection so the public no-arg ctor must exist.
 */
@QuarkusTest
class CommentCreatedEventDeserializerTest {

    @Test
    void noArgConstructor_isPublicAndUsable() {
        CommentCreatedEventDeserializer d = new CommentCreatedEventDeserializer();
        assertNotNull(d);
    }

    @Test
    void deserialize_validJson_returnsRecord() {
        UUID author = UUID.randomUUID();
        String json = "{\"commentId\":10,\"eventId\":42,\"authorId\":\"" + author + "\","
                + "\"parentCommentId\":null,\"createdAt\":\"2026-05-19T10:00:00Z\","
                + "\"content\":\"Hello @bob\",\"eventTitle\":\"Concert d'ouverture\"}";

        try (CommentCreatedEventDeserializer d = new CommentCreatedEventDeserializer()) {
            CommentCreatedEvent ev = d.deserialize("comments.created", json.getBytes(StandardCharsets.UTF_8));
            assertNotNull(ev);
            assertEquals(10L, ev.commentId());
            assertEquals(42L, ev.eventId());
            assertEquals(author, ev.authorId());
            assertNull(ev.parentCommentId());
            assertEquals("Hello @bob", ev.content());
            assertEquals("Concert d'ouverture", ev.eventTitle());
        }
    }

    @Test
    void deserialize_replyPayload_capturesParentCommentId() {
        String json = "{\"commentId\":11,\"eventId\":42,\"authorId\":\""
                + UUID.randomUUID() + "\",\"parentCommentId\":10,"
                + "\"createdAt\":\"2026-05-19T10:00:00Z\",\"content\":\"re\",\"eventTitle\":\"E\"}";

        try (CommentCreatedEventDeserializer d = new CommentCreatedEventDeserializer()) {
            CommentCreatedEvent ev = d.deserialize("comments.created", json.getBytes(StandardCharsets.UTF_8));
            assertNotNull(ev);
            assertEquals(10L, ev.parentCommentId());
        }
    }

    @Test
    void deserialize_nullBytes_returnsNull() {
        try (CommentCreatedEventDeserializer d = new CommentCreatedEventDeserializer()) {
            assertNull(d.deserialize("comments.created", null));
        }
    }
}
