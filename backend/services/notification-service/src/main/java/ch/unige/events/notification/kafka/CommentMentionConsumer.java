package ch.unige.events.notification.kafka;

import ch.unige.events.notification.entity.NotificationType;
import ch.unige.events.notification.service.NotificationService;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.IdProjection;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.kafka.events.CommentCreatedEvent;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Consumes {@code comments.created} on its own consumer-group and emits
 * a {@link NotificationType#COMMENT_MENTION} notification for every
 * {@code @<handle>} mention parsed from the comment body — SCRUM-145.
 *
 * <p>Algorithm :
 * <ol>
 *   <li>Parse {@code ev.content()} via {@link MentionParser} → handles.</li>
 *   <li>If empty, return (no DB hits, no hops).</li>
 *   <li>Batch-resolve the handles to UUIDs via the internal endpoint
 *       {@code GET /users/_internal-by-usernames} (1 hop regardless of N).
 *       Unknown handles are simply absent from the response — silent skip
 *       per Décision M.</li>
 *   <li>Resolve the comment author's display name once via
 *       {@code GET /users/{id}} for the message template
 *       ({@link #resolveAuthorLabel}).</li>
 *   <li>For each resolved mentioned user that is not the author (locked-in
 *       #11 — self-mentions ignored) emit one notification.</li>
 * </ol>
 *
 * <p>Failure posture (at-least-once / Décision C) :
 * <ul>
 *   <li>Downstream user-service unavailable → {@link UserServiceClient}'s
 *       fallback returns an empty list, the consumer produces zero
 *       notifications, the offset commits cleanly. A missed mention
 *       is acceptable ; a crashed consumer is not.</li>
 *   <li>Author resolution failure → fallback to {@code "Un utilisateur"}
 *       label so the notification still ships, just degraded.</li>
 *   <li>Profile privacy ({@code profilePublic=false}) is irrelevant here :
 *       a mention is an attention signal, not a profile read. Notifying
 *       a private user is intentional (Décision M).</li>
 * </ul>
 *
 * <p>Independence from {@code NewCommentConsumer} : both consumers run on
 * the same {@code comments.created} topic with different group-ids, so
 * mentioning an event organiser in a comment results in two separate
 * notification rows (one {@code COMMENT_MENTION} + one {@code NEW_COMMENT})
 * — different intents, different rows. Locked-in #13 / Décision K.
 */
@ApplicationScoped
public class CommentMentionConsumer {

    /** Format args : authorLabel, eventTitle. */
    private static final String MESSAGE_TEMPLATE =
            "%s vous a mentionné dans un commentaire sur « %s ».";

    /** Fallback when author resolution fails — keep the notif informative. */
    private static final String FALLBACK_AUTHOR_LABEL = "Un utilisateur";

    /** Last-resort title when the legacy payload omits eventTitle. */
    private static final String FALLBACK_EVENT_TITLE = "un événement";

    private final NotificationService notificationService;
    private final UserServiceClient userClient;
    private final MentionParser mentionParser;

    @Inject
    public CommentMentionConsumer(NotificationService notificationService,
                                  @RestClient UserServiceClient userClient,
                                  MentionParser mentionParser) {
        this.notificationService = notificationService;
        this.userClient = userClient;
        this.mentionParser = mentionParser;
    }

    @Incoming("comments-mentions")
    @Transactional
    public void onCommentCreated(CommentCreatedEvent ev) {
        // Diagnostic INFO — surfaces in pod logs so an operator can see the
        // consumer is wired and receiving messages. Keep it cheap (length
        // only, not the raw content which may be 500 chars).
        Log.infof("[NOTIF_COMMENT_MENTION_RX] comment=%d event=%d author=%s contentLen=%d eventTitle=%s",
                ev.commentId(), ev.eventId(), ev.authorId(),
                ev.content() == null ? -1 : ev.content().length(),
                ev.eventTitle() == null ? "(null)" : "\"" + ev.eventTitle() + "\"");
        Set<String> handles = mentionParser.extractHandles(ev.content());
        if (handles.isEmpty()) {
            // Common case — most comments contain no @ mention. Short-circuit
            // without hitting user-service.
            return;
        }
        Log.infof("[NOTIF_COMMENT_MENTION_PARSE] comment=%d handles=%s", ev.commentId(), handles);

        List<IdProjection> resolved;
        try {
            resolved = userClient.getByUsernames(String.join(",", handles));
        } catch (RuntimeException e) {
            // Defense in depth — the @Fallback should already convert downstream
            // failures into an empty list, but a misbehaving impl could still
            // throw. Crashing the consumer's @Incoming channel would block every
            // subsequent comment delivery — strictly worse than missing a notif.
            Log.warnf(e, "[NOTIF_COMMENT_MENTION_SKIPPED] comment=%d event=%d — user-service handle resolution failed",
                    ev.commentId(), ev.eventId());
            return;
        }
        if (resolved == null || resolved.isEmpty()) {
            Log.infof("[NOTIF_COMMENT_MENTION_NO_MATCH] comment=%d handles=%s resolved 0 users (all unknown handles)",
                    ev.commentId(), handles);
            return;
        }
        Log.infof("[NOTIF_COMMENT_MENTION_RESOLVE] comment=%d resolved=%d/%d",
                ev.commentId(), resolved.size(), handles.size());

        String authorLabel = resolveAuthorLabel(ev.authorId());
        String eventTitle = ev.eventTitle() == null || ev.eventTitle().isBlank()
                ? FALLBACK_EVENT_TITLE
                : ev.eventTitle();
        String message = MESSAGE_TEMPLATE.formatted(authorLabel, eventTitle);

        for (IdProjection target : resolved) {
            if (target.id() == null) {
                continue;
            }
            if (target.id().equals(ev.authorId())) {
                // Self-mention — locked-in #11.
                continue;
            }
            notificationService.create(target.id(), NotificationType.COMMENT_MENTION,
                    ev.eventId(), ev.authorId(), message);
            Log.infof("[NOTIF_COMMENT_MENTION] notified=%s for event=%d (mention=@%s, author=%s)",
                    target.id(), ev.eventId(), target.username(), ev.authorId());
        }
    }

    /**
     * Resolves the comment author's display label, in order of preference :
     * {@code displayName} → {@code "@" + username} → {@link #FALLBACK_AUTHOR_LABEL}.
     * Matches the frontend {@code userDisplayLabel} fallback chain.
     */
    String resolveAuthorLabel(UUID authorId) {
        if (authorId == null) {
            return FALLBACK_AUTHOR_LABEL;
        }
        UserPublicResponse author;
        try {
            author = userClient.getById(authorId);
        } catch (RuntimeException e) {
            Log.warnf(e, "[NOTIF_COMMENT_MENTION_AUTHOR_LOOKUP_FAIL] authorId=%s — using fallback label",
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
