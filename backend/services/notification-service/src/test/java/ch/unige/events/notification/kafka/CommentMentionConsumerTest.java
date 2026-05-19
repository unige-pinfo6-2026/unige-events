package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.Notification;
import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.IdProjection;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.kafka.events.CommentCreatedEvent;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

/**
 * REST + DB-layer tests for {@link CommentMentionConsumer}.
 * @SuppressWarnings("java:S1612") for the {@code Notification::deleteAll}
 * lambda — same convention as the existing notification consumers' tests.
 */
@SuppressWarnings("java:S1612")
@QuarkusTest
class CommentMentionConsumerTest {

    @Inject CommentMentionConsumer consumer;
    @InjectMock @RestClient UserServiceClient userClient;

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

    private static CommentCreatedEvent evWithContent(UUID authorId, String content, String eventTitle) {
        return CommentCreatedEvent.created(COMMENT_ID, EVENT_ID, authorId, null, content, eventTitle);
    }

    private static UserPublicResponse profile(UUID id, String username, String displayName) {
        return new UserPublicResponse(
                id, username, displayName, null, null, null, List.of(), null, null,
                false, 0L, 0L, null);
    }

    @Test
    void happyPath_singleMention_createsOne() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(userClient.getByUsernames(anyString()))
                .thenReturn(List.of(new IdProjection(alice, "alice.dosh")));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob Smith"));

        consumer.onCommentCreated(evWithContent(bob, "Hello @alice.dosh", "Concert d'ouverture"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        Notification n = rows.get(0);
        assertEquals(alice, n.userId);
        assertEquals(NotificationType.COMMENT_MENTION, n.type);
        assertEquals(bob, n.relatedUserId);
        assertNotNull(n.message);
        assertTrue(n.message.contains("Bob Smith"), "should embed author displayName");
        assertTrue(n.message.contains("Concert d'ouverture"), "should embed event title");
    }

    @Test
    void happyPath_multipleMentions_createsN() {
        UUID alice = UUID.randomUUID();
        UUID charlie = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(userClient.getByUsernames(anyString()))
                .thenReturn(List.of(
                        new IdProjection(alice, "alice.dosh"),
                        new IdProjection(charlie, "charlie.x")));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob"));

        consumer.onCommentCreated(evWithContent(bob, "@alice.dosh @charlie.x", "Workshop"));

        assertEquals(2, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void dedup_singleMentionForRepeats() {
        // The parser itself dedups via LinkedHashSet ; this pins the
        // end-to-end contract.
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(userClient.getByUsernames(anyString()))
                .thenReturn(List.of(new IdProjection(alice, "alice.dosh")));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob"));

        consumer.onCommentCreated(evWithContent(bob,
                "@alice.dosh @alice.dosh @alice.dosh", "Workshop"));

        assertEquals(1, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void selfMention_skipped() {
        // Bob mentions himself — locked-in #11 says no self-notification.
        UUID bob = UUID.randomUUID();
        when(userClient.getByUsernames(anyString()))
                .thenReturn(List.of(new IdProjection(bob, "bob.smith")));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob"));

        consumer.onCommentCreated(evWithContent(bob, "Note to self @bob.smith", "Workshop"));

        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void unresolvedMention_skipped() {
        // user-service returns empty list — the handle did not match any user.
        UUID bob = UUID.randomUUID();
        when(userClient.getByUsernames(anyString())).thenReturn(List.of());

        consumer.onCommentCreated(evWithContent(bob, "@ghost.user nobody home", "Workshop"));

        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void mixedResolvedAndUnresolved_onlyResolvedNotified() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(userClient.getByUsernames(anyString()))
                .thenReturn(List.of(new IdProjection(alice, "alice.dosh")));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob"));

        consumer.onCommentCreated(evWithContent(bob, "@alice.dosh @ghost", "Workshop"));

        assertEquals(1, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void noContent_skipped() {
        UUID bob = UUID.randomUUID();
        consumer.onCommentCreated(evWithContent(bob, null, "Workshop"));
        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void blankContent_skipped() {
        UUID bob = UUID.randomUUID();
        consumer.onCommentCreated(evWithContent(bob, "   \t  ", "Workshop"));
        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void noMentions_skipped() {
        UUID bob = UUID.randomUUID();
        consumer.onCommentCreated(evWithContent(bob, "Just a normal comment.", "Workshop"));
        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void userClientThrows_handledGracefully_noCrash() {
        UUID bob = UUID.randomUUID();
        when(userClient.getByUsernames(anyString()))
                .thenThrow(new RuntimeException("downstream boom"));

        // Must not propagate the exception — Kafka offset should commit cleanly.
        consumer.onCommentCreated(evWithContent(bob, "@alice.dosh", "Workshop"));

        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void authorResolutionFails_useFallbackLabel() {
        // Mention is created with the fallback "Un utilisateur" label.
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(userClient.getByUsernames(anyString()))
                .thenReturn(List.of(new IdProjection(alice, "alice.dosh")));
        when(userClient.getById(bob)).thenReturn(null);

        consumer.onCommentCreated(evWithContent(bob, "@alice.dosh hi", "Workshop"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("Un utilisateur"),
                "fallback label should be in the message");
    }

    @Test
    void authorWithoutDisplayName_fallsBackToAtUsername() {
        // displayName null → "@" + username is used in the message.
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(userClient.getByUsernames(anyString()))
                .thenReturn(List.of(new IdProjection(alice, "alice.dosh")));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", /* displayName */ null));

        consumer.onCommentCreated(evWithContent(bob, "@alice.dosh", "Workshop"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("@bob.smith"),
                "message should use @username when displayName is null");
    }

    @Test
    void nullEventTitle_fallsBackToGenericLabel() {
        // Legacy producer omits eventTitle → notification still ships with
        // a generic placeholder rather than emitting null in the message.
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(userClient.getByUsernames(anyString()))
                .thenReturn(List.of(new IdProjection(alice, "alice.dosh")));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob"));

        consumer.onCommentCreated(evWithContent(bob, "@alice.dosh", /* eventTitle */ null));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("un événement"),
                "fallback event title should appear in the message");
    }

    @Test
    void mentionTargetHasPrivateProfile_stillNotified() {
        // SCRUM-145 Décision M — privacy on the target profile does not gate
        // the mention. The resolver returned the row, the notif is created.
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(userClient.getByUsernames(anyString()))
                .thenReturn(List.of(new IdProjection(alice, "alice.dosh")));
        // The CommentMentionConsumer never reads `profilePublic` ; the
        // anti-oracle is enforced at GET /users/{id} when the mentioned user
        // actually clicks the notif.
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob"));

        consumer.onCommentCreated(evWithContent(bob, "@alice.dosh", "Workshop"));

        assertEquals(1, Notification.count("eventId", EVENT_ID));
    }
}
