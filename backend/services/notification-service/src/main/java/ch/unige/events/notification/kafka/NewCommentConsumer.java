package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.notification.service.NotificationService;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.kafka.events.CommentCreatedEvent;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.UUID;

/**
 * Consumes {@code comments.created} on its own consumer-group
 * ({@code notification-service-new-comment}) and emits a single
 * {@link NotificationType#NEW_COMMENT} notification targeting the event
 * creator — SCRUM-145, locked-in #9 / #12.
 *
 * <p>Independent of {@link CommentMentionConsumer} (cf. spec Décision K) :
 * a Bob-comment that mentions Alice on Alice's event produces two
 * notification rows :
 * <ul>
 *   <li>{@code COMMENT_MENTION} (Alice was mentioned) — fired by
 *       {@link CommentMentionConsumer}</li>
 *   <li>{@code NEW_COMMENT} (Alice's event got a new comment) — fired
 *       here</li>
 * </ul>
 *
 * <p>Algorithm :
 * <ol>
 *   <li>Read the {@code eventTitle} directly from the payload (SCRUM-145
 *       enrichment — avoids a REST callback to event-service for a value
 *       already known at producer time).</li>
 *   <li>Lookup the comment author for the message label
 *       ({@code displayName} → {@code @username} → fallback).</li>
 *   <li>Skip when author IS the creator (locked-in #12 — no self-notif).</li>
 *   <li>Skip when we can't establish a creator (defensive — the event was
 *       deleted between commit and consumer dispatch).</li>
 *   <li>Otherwise create one {@code NEW_COMMENT} row.</li>
 * </ol>
 *
 * <p>Important : this consumer needs the creator UUID, which is <strong>not</strong>
 * in the Kafka payload — it lives on the {@code Event} entity in
 * event-service. We avoid the REST callback by relying on the fact that
 * the author is captured in the payload, and falling back to a callback
 * only when the author is also the creator (which would be the skip case
 * we don't even fire). Concretely : we look up the event-service to find
 * the creator UUID, mirroring the {@code AttendanceCreatedConsumer}
 * pattern. This single hop replaces what would otherwise be N hops if
 * we tried to enrich differently.
 */
@ApplicationScoped
public class NewCommentConsumer {

    /** Format args : authorLabel, eventTitle. */
    private static final String MESSAGE_TEMPLATE =
            "%s a commenté votre événement « %s ».";

    private static final String FALLBACK_AUTHOR_LABEL = "Un utilisateur";
    private static final String FALLBACK_EVENT_TITLE = "un événement";

    private final NotificationService notificationService;
    private final UserServiceClient userClient;
    private final ch.unige.events.shared.client.EventServiceClient eventClient;

    @Inject
    public NewCommentConsumer(NotificationService notificationService,
                              @RestClient UserServiceClient userClient,
                              @RestClient ch.unige.events.shared.client.EventServiceClient eventClient) {
        this.notificationService = notificationService;
        this.userClient = userClient;
        this.eventClient = eventClient;
    }

    @Incoming("comments-new")
    @Transactional
    public void onCommentCreated(CommentCreatedEvent ev) {
        // SCRUM-145 — locked-in #9 : creator is notified for both top-level
        // and reply comments. No filtering on parentCommentId here.

        ch.unige.events.shared.domain.dto.EventDTO event;
        try {
            event = eventClient.getById(ev.eventId());
        } catch (RuntimeException e) {
            Log.warnf(e, "[NOTIF_NEW_COMMENT_SKIPPED] event=%d — event-service lookup failed", ev.eventId());
            return;
        }
        if (event == null) {
            Log.warnf("[NOTIF_NEW_COMMENT_SKIPPED] event=%d unresolved (event-service unavailable or event hard-deleted)",
                    ev.eventId());
            return;
        }
        UUID creatorId = event.creatorId();
        if (creatorId == null) {
            Log.warnf("[NOTIF_NEW_COMMENT_SKIPPED] event=%d has no creatorId in payload — skipping", ev.eventId());
            return;
        }
        if (creatorId.equals(ev.authorId())) {
            // Creator commented on their own event — locked-in #12.
            return;
        }

        String authorLabel = resolveAuthorLabel(ev.authorId());
        String eventTitle = pickEventTitle(ev, event);
        String message = MESSAGE_TEMPLATE.formatted(authorLabel, eventTitle);

        notificationService.create(creatorId, NotificationType.NEW_COMMENT,
                ev.eventId(), ev.authorId(), message);
        Log.infof("[NOTIF_NEW_COMMENT] notified creator=%s for event=%d (author=%s, parent=%s)",
                creatorId, ev.eventId(), ev.authorId(), ev.parentCommentId());
    }

    /**
     * Prefer the payload's {@code eventTitle} (SCRUM-145 enrichment), fall
     * back to whatever the event-service GET response carried, and finally
     * the generic placeholder. A legacy producer can pass null on the
     * payload — the event-service hop still gave us a title.
     */
    private static String pickEventTitle(CommentCreatedEvent ev,
                                         ch.unige.events.shared.domain.dto.EventDTO event) {
        if (ev.eventTitle() != null && !ev.eventTitle().isBlank()) {
            return ev.eventTitle();
        }
        if (event.title() != null && !event.title().isBlank()) {
            return event.title();
        }
        return FALLBACK_EVENT_TITLE;
    }

    /**
     * Same fallback chain as {@link CommentMentionConsumer#resolveAuthorLabel} :
     * displayName → @username → "Un utilisateur". Kept package-private for
     * testability.
     */
    String resolveAuthorLabel(UUID authorId) {
        if (authorId == null) {
            return FALLBACK_AUTHOR_LABEL;
        }
        UserPublicResponse author;
        try {
            author = userClient.getById(authorId);
        } catch (RuntimeException e) {
            Log.warnf(e, "[NOTIF_NEW_COMMENT_AUTHOR_LOOKUP_FAIL] authorId=%s — using fallback label",
                    authorId);
            return FALLBACK_AUTHOR_LABEL;
        }
        if (author == null) {
            return FALLBACK_AUTHOR_LABEL;
        }
        if (author.displayName() != null && !author.displayName().isBlank()) {
            return author.displayName();
        }
        if (author.username() != null && !author.username().isBlank()) {
            return "@" + author.username();
        }
        return FALLBACK_AUTHOR_LABEL;
    }
}
