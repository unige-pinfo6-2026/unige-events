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

/**
 * SCRUM-140 — UserFollowedConsumer test suite.
 *
 * <p>{@code @QuarkusTest} so quarkus-jacoco instruments the consumer bytecode
 * for Sonar coverage (piège #1 — standalone JUnit tests on @ApplicationScoped
 * beans are NOT counted by jacoco).
 *
 * <p>{@code java:S1612 suppressed} on {@code () -> Notification.deleteAll()}
 * lambdas (piège #4 — method reference resolves to PanacheEntityBase stub
 * that throws ; lambda forces runtime dispatch to enhanced static).
 */
@SuppressWarnings("java:S1612")
@QuarkusTest
class UserFollowedConsumerTest {

    @Inject UserFollowedConsumer consumer;
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
    void onFollowed_populatesNotificationWithDisplayName() {
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        when(userClient.getById(follower)).thenReturn(userWithDisplayName(follower, "Marie Dupont"));

        consumer.onFollowed(FollowLifecycleEvent.followed(follower, followed));

        List<Notification> notifs = Notification.<Notification>list("userId", followed);
        assertEquals(1, notifs.size());
        Notification n = notifs.get(0);
        assertEquals(NotificationType.NEW_FOLLOWER, n.type);
        assertEquals(follower, n.relatedUserId);
        assertNull(n.eventId);
        assertNotNull(n.message);
        assertTrue(n.message.contains("Marie Dupont"));
        assertTrue(n.message.contains("a commencé à vous suivre"));
    }

    @Test
    void onFollowed_userServiceFallback_usesGenericMessage() {
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        when(userClient.getById(follower)).thenReturn(null);

        consumer.onFollowed(FollowLifecycleEvent.followed(follower, followed));

        List<Notification> notifs = Notification.<Notification>list("userId", followed);
        assertEquals(1, notifs.size());
        assertEquals("Un utilisateur a commencé à vous suivre.", notifs.get(0).message);
        assertEquals(follower, notifs.get(0).relatedUserId);
    }

    @Test
    void onFollowed_blankDisplayName_usesGenericMessage() {
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        when(userClient.getById(follower)).thenReturn(userWithDisplayName(follower, "   "));

        consumer.onFollowed(FollowLifecycleEvent.followed(follower, followed));

        List<Notification> notifs = Notification.<Notification>list("userId", followed);
        assertEquals(1, notifs.size());
        assertEquals("Un utilisateur a commencé à vous suivre.", notifs.get(0).message);
    }

    @Test
    void onFollowed_wrongTypeOnChannel_skippedDefensive() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // Defensive: a misconfigured broker delivers a REQUESTED on this channel.
        consumer.onFollowed(FollowLifecycleEvent.followRequested(a, b));
        assertEquals(0, Notification.count());
    }

    @Test
    void onFollowed_selfLoop_skipped() {
        UUID same = UUID.randomUUID();
        consumer.onFollowed(FollowLifecycleEvent.followed(same, same));
        assertEquals(0, Notification.count());
    }

    @Test
    void onFollowed_nullIds_skipped() {
        consumer.onFollowed(new FollowLifecycleEvent(FollowLifecycleEvent.Type.FOLLOWED, null, UUID.randomUUID(), java.time.Instant.now()));
        consumer.onFollowed(new FollowLifecycleEvent(FollowLifecycleEvent.Type.FOLLOWED, UUID.randomUUID(), null, java.time.Instant.now()));
        assertEquals(0, Notification.count());
    }

    @Test
    void onFollowed_atLeastOnceAcceptableDuplicate() {
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        when(userClient.getById(follower)).thenReturn(userWithDisplayName(follower, "Alice"));

        FollowLifecycleEvent ev = FollowLifecycleEvent.followed(follower, followed);
        consumer.onFollowed(ev);
        consumer.onFollowed(ev);

        // Décision E — at-least-once accepted, no UK to dedup.
        assertEquals(2, Notification.<Notification>list("userId", followed).size());
    }
}
