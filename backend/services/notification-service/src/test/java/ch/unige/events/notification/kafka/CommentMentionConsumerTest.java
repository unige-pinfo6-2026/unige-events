package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.Notification;
import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.shared.kafka.events.CommentMentionEvent;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link CommentMentionConsumer} after the follow-lifecycle
 * refactor (SCRUM-145). The producer in engagement-service is now
 * responsible for parsing mentions + resolving handles, so this consumer
 * just persists the notification — the tests pin that simple persistence
 * contract + the defensive guards (null id, self-mention slip-past,
 * fallback labels).
 *
 * @SuppressWarnings("java:S1612") for the {@code Notification::deleteAll}
 * lambda — same convention as other notification consumers' tests.
 */
@SuppressWarnings("java:S1612")
@QuarkusTest
class CommentMentionConsumerTest {

    @Inject CommentMentionConsumer consumer;

    private static final long COMMENT_ID = 700L;
    private static final long EVENT_ID = 701L;

    @BeforeEach
    void truncate() {
        QuarkusTransaction.requiringNew().run(() -> Notification.deleteAll());
    }

    @AfterEach
    void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> Notification.deleteAll());
    }

    private static CommentMentionEvent ev(UUID author, UUID mentioned, String authorLabel, String eventTitle) {
        return CommentMentionEvent.of(COMMENT_ID, EVENT_ID, author, mentioned, authorLabel, eventTitle);
    }

    @Test
    void happyPath_createsOneNotification() {
        UUID author = UUID.randomUUID();
        UUID mentioned = UUID.randomUUID();

        consumer.onCommentMention(ev(author, mentioned, "Bob Smith", "Concert d'ouverture"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        Notification n = rows.get(0);
        assertEquals(mentioned, n.userId);
        assertEquals(NotificationType.COMMENT_MENTION, n.type);
        assertEquals(author, n.relatedUserId);
        assertNotNull(n.message);
        assertTrue(n.message.contains("Bob Smith"), "should embed author label");
        assertTrue(n.message.contains("Concert d'ouverture"), "should embed event title");
    }

    @Test
    void nullMentionedUserId_skipped() {
        UUID author = UUID.randomUUID();

        consumer.onCommentMention(ev(author, null, "Bob Smith", "Workshop"));

        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void nullAuthorId_skipsSelfMentionGuard_createsNotification() {
        // authorId == null → the self-mention guard's first operand is false,
        // so the && short-circuits and the notification is still created
        // (covers the authorId==null branch of the self-mention guard).
        UUID mentioned = UUID.randomUUID();

        consumer.onCommentMention(ev(null, mentioned, "Bob", "Workshop"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertEquals(mentioned, rows.get(0).userId);
    }

    @Test
    void selfMention_skippedDefensively() {
        // The producer is supposed to filter self-mentions ; if one slips
        // past (test wiring, broken producer), the consumer must still
        // refuse to create the row.
        UUID author = UUID.randomUUID();

        consumer.onCommentMention(ev(author, author, "Bob Smith", "Workshop"));

        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void nullAuthorLabel_usesFallback() {
        UUID author = UUID.randomUUID();
        UUID mentioned = UUID.randomUUID();

        consumer.onCommentMention(ev(author, mentioned, null, "Workshop"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("Un utilisateur"),
                "should fall back to generic label when authorLabel is null");
    }

    @Test
    void blankAuthorLabel_usesFallback() {
        UUID author = UUID.randomUUID();
        UUID mentioned = UUID.randomUUID();

        consumer.onCommentMention(ev(author, mentioned, "   ", "Workshop"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("Un utilisateur"));
    }

    @Test
    void nullEventTitle_usesFallback() {
        UUID author = UUID.randomUUID();
        UUID mentioned = UUID.randomUUID();

        consumer.onCommentMention(ev(author, mentioned, "Bob", null));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("un événement"),
                "fallback event title should appear in the message");
    }

    @Test
    void blankEventTitle_usesFallback() {
        UUID author = UUID.randomUUID();
        UUID mentioned = UUID.randomUUID();

        consumer.onCommentMention(ev(author, mentioned, "Bob", "   "));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("un événement"));
    }

    @Test
    void multipleEvents_eachProducesOneRow() {
        // Mirrors the producer fan-out semantics : N mentions = N separate
        // Kafka messages = N consumer invocations = N notification rows.
        UUID author = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID charlie = UUID.randomUUID();

        consumer.onCommentMention(ev(author, alice, "Bob", "Workshop"));
        consumer.onCommentMention(ev(author, charlie, "Bob", "Workshop"));

        assertEquals(2, Notification.count("eventId", EVENT_ID));
    }
}
