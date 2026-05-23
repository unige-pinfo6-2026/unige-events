package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.Notification;
import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SuppressWarnings("java:S1612")
@QuarkusTest
class UserFollowAcceptedConsumerTest {

    @Inject UserFollowAcceptedConsumer consumer;
    @InjectMock @RestClient UserServiceClient userClient;

    @BeforeEach
    void truncate() {
        QuarkusTransaction.requiringNew().run(() -> Notification.deleteAll());
    }

    @AfterEach
    void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> Notification.deleteAll());
    }

    private static UserPublicResponse userWithDisplayName(UUID id, String displayName) {
        return UserPublicResponse.anonymous(id, "username-" + id, displayName, null);
    }

    /**
     * <strong>Sentinel critique Décision C.</strong> Le destinataire d'un
     * FOLLOW_ACCEPTED est l'<em>initiateur initial</em> (followerId), <strong>pas</strong>
     * l'acceptant (followedId). Inverser ces deux UUIDs est une faute fonctionnelle
     * bloquante (la notif partirait à la mauvaise personne).
     *
     * <p>Le {@code displayName} affiché dans le message est celui de l'<em>acceptant</em>
     * (« Alice a accepté votre demande de suivi. »).
     */
    @Test
    void destinationIsInitiatorNotAcceptor() {
        UUID initiator = UUID.randomUUID();  // originally the follower in the pending request
        UUID acceptor = UUID.randomUUID();   // owner of the private profile, who just accepted
        when(userClient.getById(acceptor)).thenReturn(userWithDisplayName(acceptor, "Alice Acceptor"));

        consumer.onFollowAccepted(FollowLifecycleEvent.followAccepted(initiator, acceptor));

        // The INITIATOR receives the notification.
        List<Notification> initiatorNotifs = Notification.<Notification>list("userId", initiator);
        assertEquals(1, initiatorNotifs.size());
        Notification n = initiatorNotifs.get(0);
        assertEquals(NotificationType.FOLLOW_ACCEPTED, n.type);
        assertEquals(acceptor, n.relatedUserId, "relatedUserId must be the acceptor (followedId)");
        assertNull(n.eventId);
        assertNotNull(n.message);
        assertTrue(n.message.contains("Alice Acceptor"), "message embeds acceptor displayName");
        assertTrue(n.message.contains("a accepté"));

        // The ACCEPTOR receives NOTHING (their own action — no self-notification).
        assertEquals(0, Notification.<Notification>list("userId", acceptor).size());
    }

    @Test
    void onFollowAccepted_resolvesAcceptorDisplayName_notInitiator() {
        UUID initiator = UUID.randomUUID();
        UUID acceptor = UUID.randomUUID();
        // Mock only the acceptor — if the consumer accidentally calls getById(initiator),
        // it would return null and the test would observe the fallback message.
        when(userClient.getById(acceptor)).thenReturn(userWithDisplayName(acceptor, "Bob"));

        consumer.onFollowAccepted(FollowLifecycleEvent.followAccepted(initiator, acceptor));

        List<Notification> notifs = Notification.<Notification>list("userId", initiator);
        assertEquals(1, notifs.size());
        assertTrue(notifs.get(0).message.contains("Bob"), "consumer must resolve acceptor, not initiator");
    }

    @Test
    void onFollowAccepted_userServiceFallback_usesGenericMessage() {
        UUID initiator = UUID.randomUUID();
        UUID acceptor = UUID.randomUUID();
        when(userClient.getById(acceptor)).thenReturn(null);

        consumer.onFollowAccepted(FollowLifecycleEvent.followAccepted(initiator, acceptor));

        List<Notification> notifs = Notification.<Notification>list("userId", initiator);
        assertEquals(1, notifs.size());
        assertEquals("Votre demande de suivi a été acceptée.", notifs.get(0).message);
        assertEquals(acceptor, notifs.get(0).relatedUserId);
    }

    @Test
    void onFollowAccepted_nullFollowerId_skipped() {
        consumer.onFollowAccepted(FollowLifecycleEvent.followAccepted(null, UUID.randomUUID()));
        assertEquals(0, Notification.count());
    }

    @Test
    void onFollowAccepted_nullFollowedId_skipped() {
        consumer.onFollowAccepted(FollowLifecycleEvent.followAccepted(UUID.randomUUID(), null));
        assertEquals(0, Notification.count());
    }

    @Test
    void onFollowAccepted_blankDisplayName_usesGenericMessage() {
        // resolveMessage: acceptor resolved but displayName blank → the
        // isBlank() sub-branch of line 87 falls back to the generic message.
        UUID initiator = UUID.randomUUID();
        UUID acceptor = UUID.randomUUID();
        when(userClient.getById(acceptor)).thenReturn(userWithDisplayName(acceptor, "   "));

        consumer.onFollowAccepted(FollowLifecycleEvent.followAccepted(initiator, acceptor));

        List<Notification> notifs = Notification.<Notification>list("userId", initiator);
        assertEquals(1, notifs.size());
        assertEquals("Votre demande de suivi a été acceptée.", notifs.get(0).message);
    }

    @Test
    void onFollowAccepted_nullDisplayName_usesGenericMessage() {
        // resolveMessage: acceptor resolved but displayName null → the
        // null sub-branch of line 87 falls back to the generic message.
        UUID initiator = UUID.randomUUID();
        UUID acceptor = UUID.randomUUID();
        when(userClient.getById(acceptor)).thenReturn(userWithDisplayName(acceptor, null));

        consumer.onFollowAccepted(FollowLifecycleEvent.followAccepted(initiator, acceptor));

        List<Notification> notifs = Notification.<Notification>list("userId", initiator);
        assertEquals(1, notifs.size());
        assertEquals("Votre demande de suivi a été acceptée.", notifs.get(0).message);
    }

    @Test
    void onFollowAccepted_wrongType_skippedDefensive() {
        consumer.onFollowAccepted(FollowLifecycleEvent.followed(UUID.randomUUID(), UUID.randomUUID()));
        assertEquals(0, Notification.count());
    }

    @Test
    void onFollowAccepted_selfLoop_skipped() {
        UUID same = UUID.randomUUID();
        consumer.onFollowAccepted(FollowLifecycleEvent.followAccepted(same, same));
        assertEquals(0, Notification.count());
    }

    @Test
    void onFollowAccepted_atLeastOnce_acceptableDuplicate() {
        UUID initiator = UUID.randomUUID();
        UUID acceptor = UUID.randomUUID();
        when(userClient.getById(acceptor)).thenReturn(userWithDisplayName(acceptor, "Bob"));

        FollowLifecycleEvent ev = FollowLifecycleEvent.followAccepted(initiator, acceptor);
        consumer.onFollowAccepted(ev);
        consumer.onFollowAccepted(ev);
        assertEquals(2, Notification.<Notification>list("userId", initiator).size());
    }
}
