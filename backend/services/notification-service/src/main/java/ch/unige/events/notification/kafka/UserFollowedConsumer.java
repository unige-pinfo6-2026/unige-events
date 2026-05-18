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
 * Consumes {@code users.followed} (SCRUM-140). Posts a single
 * {@link NotificationType#NEW_FOLLOWER} notification targeting the
 * {@code followedId} when a public-profile user receives a new follower
 * (auto-accepted follow path — {@link FollowLifecycleEvent.Type#FOLLOWED}).
 *
 * <p>Message template uses the follower's {@code displayName} resolved via
 * {@link UserServiceClient#getById(UUID)}. If the REST call falls back
 * ({@code null} on downstream outage, 404 on a private-profile follower
 * responding ISSUE-93 anti-oracle, or deleted user), the consumer falls
 * back to a generic message and still persists the notification — the
 * {@code relatedUserId} stays populated so the frontend can re-resolve
 * the actor cliquably.
 *
 * <p>At-least-once semantics accepted (Décision E — mirror Décision D SCRUM-99
 * phase 1). A redelivered Kafka message produces a duplicate notification
 * row ; a user who follow → unfollow → re-follow sees 2 NEW_FOLLOWER notifs
 * — that's the expected UX.
 *
 * <p>Self-loop defensive skip : if {@code followerId == followedId} (impossible
 * upstream — {@code FollowService.follow} rejects with 422 {@code cannot_follow_self})
 * the consumer logs a WARN and returns without persisting. Belt-and-suspenders
 * against test wiring / broker corruption.
 */
@ApplicationScoped
public class UserFollowedConsumer {

    private static final String MESSAGE_TEMPLATE = "%s a commencé à vous suivre.";
    private static final String MESSAGE_FALLBACK = "Un utilisateur a commencé à vous suivre.";

    private final NotificationService notificationService;
    private final UserServiceClient userClient;

    @Inject
    public UserFollowedConsumer(NotificationService notificationService,
                                @RestClient UserServiceClient userClient) {
        this.notificationService = notificationService;
        this.userClient = userClient;
    }

    @Incoming("users-followed")
    @Transactional
    public void onFollowed(FollowLifecycleEvent ev) {
        // Safety filter — the channel topic already selects FOLLOWED, but a
        // misconfigured broker or test wiring could deliver another type.
        if (ev.type() != FollowLifecycleEvent.Type.FOLLOWED) {
            Log.warnf("[NOTIF_NEW_FOLLOWER_SKIPPED] unexpected type=%s on users-followed channel", ev.type());
            return;
        }
        UUID followerId = ev.followerId();
        UUID followedId = ev.followedId();
        if (followerId == null || followedId == null) {
            Log.warnf("[NOTIF_NEW_FOLLOWER_SKIPPED] null id (follower=%s, followed=%s)", followerId, followedId);
            return;
        }
        // Defensive self-loop skip — upstream FollowService rejects 422 self-follow.
        if (followerId.equals(followedId)) {
            Log.warnf("[NOTIF_NEW_FOLLOWER_SKIPPED] self-loop (followerId == followedId == %s)", followerId);
            return;
        }
        String message = resolveMessage(followerId);
        notificationService.create(followedId, NotificationType.NEW_FOLLOWER, null, followerId, message);
        Log.infof("[NOTIF_NEW_FOLLOWER] notified user=%s of new follower=%s", followedId, followerId);
    }

    private String resolveMessage(UUID followerId) {
        UserPublicResponse follower = userClient.getById(followerId);
        if (follower == null || follower.displayName() == null || follower.displayName().isBlank()) {
            return MESSAGE_FALLBACK;
        }
        return MESSAGE_TEMPLATE.formatted(follower.displayName());
    }
}
