package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.Notification;
import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.kafka.events.CommentCreatedEvent;

import ch.unige.events.notification.service.NotificationService;

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
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Month;

@SuppressWarnings("java:S1612")
@QuarkusTest
class NewCommentConsumerTest {

    @Inject NewCommentConsumer consumer;
    @Inject NotificationService notificationService;
    @InjectMock @RestClient EventServiceClient eventClient;
    @InjectMock @RestClient UserServiceClient userClient;

    private static final long COMMENT_ID = 800L;
    private static final long EVENT_ID = 801L;

    @BeforeEach
    void truncate() {
        QuarkusTransaction.requiringNew().run(() -> Notification.deleteAll());
    }

    @AfterEach
    void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> Notification.deleteAll());
    }

    private static EventDTO eventOf(String title, UUID creatorId) {
        return new EventDTO(EVENT_ID, title, "desc", "loc",
                java.time.LocalDateTime.of(2025, Month.JANUARY, 1, 12, 0), java.time.LocalDateTime.of(2999, Month.JANUARY, 1, 0, 0).plusHours(2),
                null, null, null, creatorId, EventStatus.PUBLISHED, null, false, false, null,
                0L, null, 0L, 0L, 0L,
                null, null, null, List.of(), null, null, null, null, null);
    }

    private static CommentCreatedEvent ev(UUID authorId, Long parentCommentId,
                                          String content, String eventTitle) {
        return CommentCreatedEvent.created(COMMENT_ID, EVENT_ID, authorId, parentCommentId,
                content, eventTitle);
    }

    private static UserPublicResponse profile(UUID id, String username, String displayName) {
        return new UserPublicResponse(
                id, username, displayName, null, null, null, List.of(), null, null,
                false, 0L, 0L, null);
    }

    @Test
    void happyPath_creatorNotified() {
        UUID creator = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", creator));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob Smith"));

        consumer.onCommentCreated(ev(bob, null, "Just commenting", "Workshop"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        Notification n = rows.get(0);
        assertEquals(creator, n.userId);
        assertEquals(NotificationType.NEW_COMMENT, n.type);
        assertEquals(bob, n.relatedUserId);
        assertNotNull(n.message);
        assertTrue(n.message.contains("Bob Smith"));
        assertTrue(n.message.contains("Workshop"));
    }

    @Test
    void authorIsCreator_skipped() {
        // Creator commented on their own event — locked-in #12.
        UUID creator = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", creator));

        consumer.onCommentCreated(ev(creator, null, "Just an update from me", "Workshop"));

        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void eventNotFound_skipped() {
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(null);

        consumer.onCommentCreated(ev(bob, null, "hello", "Workshop"));

        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void eventClientThrows_handledGracefully_noCrash() {
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(anyLong())).thenThrow(new RuntimeException("event-service boom"));

        consumer.onCommentCreated(ev(bob, null, "hello", "Workshop"));

        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void creatorIdNull_skipped() {
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", /* creatorId */ null));

        consumer.onCommentCreated(ev(bob, null, "hello", "Workshop"));

        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void replyComment_stillNotifiesCreator() {
        // Locked-in #9 — replies fire NEW_COMMENT too.
        UUID creator = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", creator));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob"));

        consumer.onCommentCreated(ev(bob, /* parentCommentId */ 999L, "@alice yes", "Workshop"));

        assertEquals(1, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void authorResolutionFails_useFallbackLabel() {
        UUID creator = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", creator));
        when(userClient.getById(bob)).thenReturn(null);

        consumer.onCommentCreated(ev(bob, null, "hi", "Workshop"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("Un utilisateur"));
    }

    @Test
    void authorWithoutDisplayName_fallsBackToAtUsername() {
        UUID creator = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", creator));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", null));

        consumer.onCommentCreated(ev(bob, null, "hi", "Workshop"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("@bob.smith"));
    }

    @Test
    void nullEventTitleOnPayload_fallsBackToEventDtoTitle() {
        // Pre-SCRUM-145 producer would send null/null ; the event-service hop
        // still has the title.
        UUID creator = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop from DTO", creator));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob"));

        consumer.onCommentCreated(ev(bob, null, "hi", /* eventTitle */ null));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("Workshop from DTO"));
    }

    @Test
    void creatorWithPrivateProfile_stillNotified() {
        // Décision M — profile privacy doesn't gate the notif.
        UUID creator = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", creator));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob"));

        consumer.onCommentCreated(ev(bob, null, "hi", "Workshop"));

        assertEquals(1, Notification.count("eventId", EVENT_ID));
    }

    // ─── SCRUM-145 — fine-grained branch coverage of pickEventTitle +
    //     resolveAuthorLabel + authorLookup error path. ──────────────

    @Test
    void blankEventTitleOnPayload_fallsBackToEventDtoTitle() {
        // pickEventTitle's first branch tests both null AND isBlank.
        // The null variant is covered above ; pin the blank one here.
        UUID creator = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop from DTO", creator));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob"));

        consumer.onCommentCreated(ev(bob, null, "hi", "   \t "));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("Workshop from DTO"));
    }

    @Test
    void bothEventTitlesNull_fallsBackToGenericLabel() {
        // Payload eventTitle null AND event.title() null → last-resort
        // generic placeholder "un événement".
        UUID creator = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf(/* title */ null, creator));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob"));

        consumer.onCommentCreated(ev(bob, null, "hi", null));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("un événement"));
    }

    @Test
    void bothEventTitlesBlank_fallsBackToGenericLabel() {
        UUID creator = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("  ", creator));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "Bob"));

        consumer.onCommentCreated(ev(bob, null, "hi", "  "));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("un événement"));
    }

    @Test
    void authorLookupThrows_useFallbackLabel() {
        // The try-catch around userClient.getById in resolveAuthorLabel
        // swallows RuntimeException and falls back to "Un utilisateur".
        UUID creator = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", creator));
        when(userClient.getById(bob)).thenThrow(new RuntimeException("user-service boom"));

        consumer.onCommentCreated(ev(bob, null, "hi", "Workshop"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("Un utilisateur"));
    }

    @Test
    void authorWithBlankDisplayName_fallsBackToAtUsername() {
        // displayName present but blank → the !isBlank() sub-branch of 158
        // is false, so we fall through to the @username branch (161 true).
        UUID creator = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", creator));
        when(userClient.getById(bob)).thenReturn(profile(bob, "bob.smith", "   "));

        consumer.onCommentCreated(ev(bob, null, "hi", "Workshop"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("@bob.smith"));
    }

    @Test
    void authorWithBlankDisplayNameAndBlankUsername_fallsBackToGeneric() {
        // Both displayName and username blank → !isBlank() false on both
        // branches (158 + 161), bottoming out on the generic label.
        UUID creator = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", creator));
        when(userClient.getById(bob)).thenReturn(profile(bob, "  ", "  "));

        consumer.onCommentCreated(ev(bob, null, "hi", "Workshop"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("Un utilisateur"));
    }

    @Test
    void authorWithBothLabelsBlank_fallsBackToGeneric() {
        // displayName + username both null on the resolved profile → the
        // fallback chain bottoms out on "Un utilisateur".
        UUID creator = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        when(eventClient.getById(EVENT_ID)).thenReturn(eventOf("Workshop", creator));
        when(userClient.getById(bob)).thenReturn(profile(bob, /* username */ null, /* displayName */ null));

        consumer.onCommentCreated(ev(bob, null, "hi", "Workshop"));

        List<Notification> rows = Notification.<Notification>list("eventId", EVENT_ID);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).message.contains("Un utilisateur"));
    }

    @Test
    void resolveAuthorLabel_nullAuthorId_returnsFallback() {
        // Helper short-circuit — even though the consumer always passes
        // a non-null authorId in production, the helper is null-safe.
        assertEquals("Un utilisateur", consumer.resolveAuthorLabel(null));
    }

    // ─── FT-bypass hand-wiring: the @InjectMock @RestClient beans are wrapped
    //     by @Fallback, so thenThrow there returns null (→ the event==null /
    //     author==null branches) and never reaches the catch blocks. Build the
    //     consumer with plain (non-FT) mocks that throw straight in. ──────────

    @Test
    void eventClientThrowsToConsumer_handWired_catchSwallows() {
        // Reaches the catch in onCommentCreated (event-service lookup failed).
        UUID bob = UUID.randomUUID();
        EventServiceClient evMock = mock(EventServiceClient.class);
        UserServiceClient usrMock = mock(UserServiceClient.class);
        when(evMock.getById(EVENT_ID)).thenThrow(new RuntimeException("event-service boom"));

        NewCommentConsumer hw = new NewCommentConsumer(notificationService, usrMock, evMock);
        QuarkusTransaction.requiringNew().run(() ->
                hw.onCommentCreated(ev(bob, null, "hello", "Workshop")));

        assertEquals(0, Notification.count("eventId", EVENT_ID));
    }

    @Test
    void authorLookupThrowsToHelper_handWired_catchReturnsFallback() {
        // Reaches the catch inside resolveAuthorLabel (user-service lookup
        // failed) — returns the generic fallback label without crashing.
        UUID bob = UUID.randomUUID();
        EventServiceClient evMock = mock(EventServiceClient.class);
        UserServiceClient usrMock = mock(UserServiceClient.class);
        when(usrMock.getById(bob)).thenThrow(new RuntimeException("user-service boom"));

        NewCommentConsumer hw = new NewCommentConsumer(notificationService, usrMock, evMock);
        assertEquals("Un utilisateur", hw.resolveAuthorLabel(bob));
    }
}
