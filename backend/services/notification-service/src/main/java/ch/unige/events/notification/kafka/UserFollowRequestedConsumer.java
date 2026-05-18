package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.notification.service.NotificationService;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.kafka.events.FollowLifecycleEvent;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.UUID;

/**
 * Consumes {@code users.follow-requested} (SCRUM-140). Posts a single
 * {@link NotificationType#FOLLOW_REQUEST} notification targeting the
 * {@code followedId} when a user with a private profile receives a PENDING
 * follow request ({@link FollowLifecycleEvent.Type#REQUESTED}).
 *
 * <p>Pattern strict miroir de {@link UserFollowedConsumer}. Destinataire et
 * message identiques en structure ; seul le NotificationType et le template
 * de message diffèrent. Cf. JavaDoc {@link UserFollowedConsumer} pour les
 * notes générales (at-least-once Décision E, self-loop défensif, fallback
 * REST).
 */
@ApplicationScoped
public class UserFollowRequestedConsumer {

    private static final String MESSAGE_TEMPLATE = "%s vous a envoyé une demande de suivi.";
    private static final String MESSAGE_FALLBACK = "Un utilisateur vous a envoyé une demande de suivi.";

    private final NotificationService notificationService;
    private final UserServiceClient userClient;

    @Inject
    public UserFollowRequestedConsumer(NotificationService notificationService,
                                       @RestClient UserServiceClient userClient) {
        this.notificationService = notificationService;
        this.userClient = userClient;
    }

    @Incoming("users-follow-requested")
    @Transactional
    public void onFollowRequested(FollowLifecycleEvent ev) {
        if (ev.type() != FollowLifecycleEvent.Type.REQUESTED) {
            Log.warnf("[NOTIF_FOLLOW_REQUEST_SKIPPED] unexpected type=%s on users-follow-requested channel", ev.type());
            return;
        }
        UUID followerId = ev.followerId();
        UUID followedId = ev.followedId();
        if (followerId == null || followedId == null) {
            Log.warnf("[NOTIF_FOLLOW_REQUEST_SKIPPED] null id (follower=%s, followed=%s)", followerId, followedId);
            return;
        }
        if (followerId.equals(followedId)) {
            Log.warnf("[NOTIF_FOLLOW_REQUEST_SKIPPED] self-loop (followerId == followedId == %s)", followerId);
            return;
        }
        String message = resolveMessage(followerId);
        notificationService.create(followedId, NotificationType.FOLLOW_REQUEST, null, followerId, message);
        Log.infof("[NOTIF_FOLLOW_REQUEST] notified user=%s of pending request from=%s", followedId, followerId);
    }

    private String resolveMessage(UUID followerId) {
        UserPublicResponse follower = userClient.getById(followerId);
        if (follower == null || follower.displayName() == null || follower.displayName().isBlank()) {
            return MESSAGE_FALLBACK;
        }
        return MESSAGE_TEMPLATE.formatted(follower.displayName());
    }
}
