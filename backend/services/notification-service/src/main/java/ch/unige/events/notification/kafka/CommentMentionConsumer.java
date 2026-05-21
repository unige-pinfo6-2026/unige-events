package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.notification.service.NotificationService;
import ch.unige.events.shared.kafka.events.CommentMentionEvent;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/**
 * Consumes {@code comments.mentions} — one Kafka message per mentioned
 * user. Mirrors {@code UserFollowedConsumer} : the producer
 * (engagement-service) has already resolved the {@code @<handle>} →
 * {@code mentionedUserId}, pre-computed the {@code authorLabel}, and
 * filtered out self-mentions, so this consumer is purely persistence.
 *
 * <p>At-least-once semantics : a redelivered Kafka message produces a
 * duplicate notification row (Décision E — mirror of follow-lifecycle).
 * Acceptable UX given the rarity of redelivery.
 *
 * <p>Defensive checks (null ids, self-loop) are belt-and-suspenders against
 * a misbehaving producer or test wiring.
 */
@ApplicationScoped
public class CommentMentionConsumer {

    /** Format args : authorLabel, eventTitle. */
    private static final String MESSAGE_TEMPLATE =
            "%s vous a mentionné dans un commentaire sur « %s ».";
    private static final String FALLBACK_EVENT_TITLE = "un événement";

    private final NotificationService notificationService;

    @Inject
    public CommentMentionConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Incoming("comments-mentions")
    @Transactional
    public void onCommentMention(CommentMentionEvent ev) {
        Log.infof("[NOTIF_COMMENT_MENTION_RX] comment=%d event=%d author=%s mentioned=%s",
                ev.commentId(), ev.eventId(), ev.authorId(), ev.mentionedUserId());

        if (ev.mentionedUserId() == null) {
            Log.warnf("[NOTIF_COMMENT_MENTION_SKIPPED] null mentionedUserId on comment=%d", ev.commentId());
            return;
        }
        if (ev.authorId() != null && ev.authorId().equals(ev.mentionedUserId())) {
            // Producer should have filtered self-mentions ; defensive skip.
            Log.warnf("[NOTIF_COMMENT_MENTION_SKIPPED] self-mention slipped past producer (comment=%d user=%s)",
                    ev.commentId(), ev.authorId());
            return;
        }

        String authorLabel = (ev.authorLabel() == null || ev.authorLabel().isBlank())
                ? "Un utilisateur"
                : ev.authorLabel();
        String eventTitle = (ev.eventTitle() == null || ev.eventTitle().isBlank())
                ? FALLBACK_EVENT_TITLE
                : ev.eventTitle();
        String message = MESSAGE_TEMPLATE.formatted(authorLabel, eventTitle);

        notificationService.create(ev.mentionedUserId(), NotificationType.COMMENT_MENTION,
                ev.eventId(), ev.authorId(), message);
        Log.infof("[NOTIF_COMMENT_MENTION] notified=%s for event=%d (author=%s)",
                ev.mentionedUserId(), ev.eventId(), ev.authorId());
    }
}
