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
class UserFollowRequestedConsumerTest {

    @Inject UserFollowRequestedConsumer consumer;
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

    @Test
    void onFollowRequested_populatesNotificationWithDisplayName() {
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        when(userClient.getById(follower)).thenReturn(userWithDisplayName(follower, "Jean Dupont"));

        consumer.onFollowRequested(FollowLifecycleEvent.followRequested(follower, followed));

        List<Notification> notifs = Notification.<Notification>list("userId", followed);
        assertEquals(1, notifs.size());
        Notification n = notifs.get(0);
        assertEquals(NotificationType.FOLLOW_REQUEST, n.type);
        assertEquals(follower, n.relatedUserId);
        assertNull(n.eventId);
        assertNotNull(n.message);
        assertTrue(n.message.contains("Jean Dupont"));
        assertTrue(n.message.contains("demande de suivi"));
    }

    @Test
    void onFollowRequested_userServiceFallback_usesGenericMessage() {
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        when(userClient.getById(follower)).thenReturn(null);

        consumer.onFollowRequested(FollowLifecycleEvent.followRequested(follower, followed));

        List<Notification> notifs = Notification.<Notification>list("userId", followed);
        assertEquals(1, notifs.size());
        assertEquals("Un utilisateur vous a envoyé une demande de suivi.", notifs.get(0).message);
        assertEquals(follower, notifs.get(0).relatedUserId);
    }

    @Test
    void onFollowRequested_nullFollowerId_skipped() {
        consumer.onFollowRequested(FollowLifecycleEvent.followRequested(null, UUID.randomUUID()));
        assertEquals(0, Notification.count());
    }

    @Test
    void onFollowRequested_nullFollowedId_skipped() {
        consumer.onFollowRequested(FollowLifecycleEvent.followRequested(UUID.randomUUID(), null));
        assertEquals(0, Notification.count());
    }

    @Test
    void onFollowRequested_blankDisplayName_usesGenericMessage() {
        // resolveMessage: follower resolved but displayName blank → the
        // isBlank() sub-branch of line 70 falls back to the generic message.
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        when(userClient.getById(follower)).thenReturn(userWithDisplayName(follower, "   "));

        consumer.onFollowRequested(FollowLifecycleEvent.followRequested(follower, followed));

        List<Notification> notifs = Notification.<Notification>list("userId", followed);
        assertEquals(1, notifs.size());
        assertEquals("Un utilisateur vous a envoyé une demande de suivi.", notifs.get(0).message);
    }

    @Test
    void onFollowRequested_nullDisplayName_usesGenericMessage() {
        // resolveMessage: follower resolved but displayName null → the
        // null sub-branch of line 70 falls back to the generic message.
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        when(userClient.getById(follower)).thenReturn(userWithDisplayName(follower, null));

        consumer.onFollowRequested(FollowLifecycleEvent.followRequested(follower, followed));

        List<Notification> notifs = Notification.<Notification>list("userId", followed);
        assertEquals(1, notifs.size());
        assertEquals("Un utilisateur vous a envoyé une demande de suivi.", notifs.get(0).message);
    }

    @Test
    void onFollowRequested_wrongType_skippedDefensive() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        consumer.onFollowRequested(FollowLifecycleEvent.followed(a, b));
        assertEquals(0, Notification.count());
    }

    @Test
    void onFollowRequested_selfLoop_skipped() {
        UUID same = UUID.randomUUID();
        consumer.onFollowRequested(FollowLifecycleEvent.followRequested(same, same));
        assertEquals(0, Notification.count());
    }

    @Test
    void onFollowRequested_atLeastOnce_acceptableDuplicate() {
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        when(userClient.getById(follower)).thenReturn(userWithDisplayName(follower, "Alice"));

        FollowLifecycleEvent ev = FollowLifecycleEvent.followRequested(follower, followed);
        consumer.onFollowRequested(ev);
        consumer.onFollowRequested(ev);
        assertEquals(2, Notification.<Notification>list("userId", followed).size());
    }
}
