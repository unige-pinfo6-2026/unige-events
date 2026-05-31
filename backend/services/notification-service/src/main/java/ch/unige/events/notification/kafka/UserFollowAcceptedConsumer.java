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
 * Consumes {@code users.follow-accepted} (SCRUM-140). Posts a single
 * {@link NotificationType#FOLLOW_ACCEPTED} notification targeting the
 * <strong>follower (initiator)</strong> when a private-profile owner accepts
 * their pending follow request ({@link FollowLifecycleEvent.Type#ACCEPTED}).
 *
 * <p><strong>Destinataire critique (Décision C).</strong> Contrairement à
 * {@link UserFollowedConsumer} qui notifie {@code followedId} (la cible), ce
 * consumer notifie <strong>{@code followerId}</strong> (l'initiateur initial,
 * qui voit sa demande de suivi acceptée). {@code relatedUserId} porte l'UUID de
 * l'acceptant pour permettre au frontend de rendre cliquable son profil.
 *
 * <p>Depuis la QA bug batch (bug ①), l'ACCEPT émet AUSSI un {@code followed}
 * (→ {@link UserFollowedConsumer} → {@link NotificationType#NEW_FOLLOWER} vers
 * l'acceptant {@code followedId}). Les deux notifications sont donc créées sur
 * un accept : FOLLOW_ACCEPTED → A (ici), NEW_FOLLOWER → B (l'autre consumer).
 *
 * <p>Inverser ces deux UUIDs est une faute fonctionnelle bloquante — cf.
 * sentinel test {@code UserFollowAcceptedConsumerTest.destinationIsInitiatorNotAcceptor}.
 *
 * <p>Le {@code displayName} résolu est celui de l'<strong>acceptant</strong>
 * (followedId), car le message UX cible est « {acceptant} a accepté votre
 * demande de suivi. ». La résolution suit le pattern fallback
 * {@link UserFollowedConsumer} en cas d'échec REST.
 *
 * <p>At-least-once accepté (Décision E). Self-loop défensif (impossible
 * upstream — un user ne peut pas accepter sa propre demande).
 */
@ApplicationScoped
public class UserFollowAcceptedConsumer {

    private static final String MESSAGE_TEMPLATE = "%s a accepté votre demande de suivi.";
    private static final String MESSAGE_FALLBACK = "Votre demande de suivi a été acceptée.";

    private final NotificationService notificationService;
    private final UserServiceClient userClient;

    @Inject
    public UserFollowAcceptedConsumer(NotificationService notificationService,
                                      @RestClient UserServiceClient userClient) {
        this.notificationService = notificationService;
        this.userClient = userClient;
    }

    @Incoming("users-follow-accepted")
    @Transactional
    public void onFollowAccepted(FollowLifecycleEvent ev) {
        if (ev.type() != FollowLifecycleEvent.Type.ACCEPTED) {
            Log.warnf("[NOTIF_FOLLOW_ACCEPTED_SKIPPED] unexpected type=%s on users-follow-accepted channel", ev.type());
            return;
        }
        UUID followerId = ev.followerId();
        UUID followedId = ev.followedId();
        if (followerId == null || followedId == null) {
            Log.warnf("[NOTIF_FOLLOW_ACCEPTED_SKIPPED] null id (follower=%s, followed=%s)", followerId, followedId);
            return;
        }
        if (followerId.equals(followedId)) {
            Log.warnf("[NOTIF_FOLLOW_ACCEPTED_SKIPPED] self-loop (followerId == followedId == %s)", followerId);
            return;
        }
        // Resolve the ACCEPTOR's displayName — they are the actor in the message text.
        String message = resolveMessage(followedId);
        // DESTINATAIRE = followerId (the initiator). relatedUserId = followedId (the acceptor).
        // Inverting these two = blocking functional bug (Décision C sentinel test).
        notificationService.create(followerId, NotificationType.FOLLOW_ACCEPTED, null, followedId, message);
        Log.infof("[NOTIF_FOLLOW_ACCEPTED] notified initiator=%s of acceptance by=%s", followerId, followedId);
    }

    private String resolveMessage(UUID acceptorId) {
        UserPublicResponse acceptor = userClient.getById(acceptorId);
        if (acceptor == null || acceptor.displayName() == null || acceptor.displayName().isBlank()) {
            return MESSAGE_FALLBACK;
        }
        return MESSAGE_TEMPLATE.formatted(acceptor.displayName());
    }
}
