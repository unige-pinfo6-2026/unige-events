package ch.unige.events.notification.entity;

/**
 * In-app notification kinds. Owned by notification-service — not in
 * {@code shared-domain-enums} because no other service consumes the enum
 * (cf. SCRUM-99 Décision C).
 *
 * <p><strong>Phase 1 values (SCRUM-99)</strong> — emitted by 3 consumers in
 * notification-service :
 * <ul>
 *   <li>{@link #EVENT_UPDATED} — emitted by {@code EventUpdatedConsumer} ←
 *       {@code events.updated}. Fans out to all ATTENDING attendees.</li>
 *   <li>{@link #EVENT_CANCELLED} — emitted by {@code EventCancelledConsumer} ←
 *       {@code events.cancelled}. Fans out to all ATTENDING attendees.</li>
 *   <li>{@link #EVENT_REMINDER} — reserved for a future scheduler (24h
 *       before start). Not wired in phase 1.</li>
 *   <li>{@link #NEW_ATTENDEE} — emitted by {@code AttendanceCreatedConsumer}
 *       ← {@code attendances.created}. Targets the event creator (skipped
 *       on self-attend).</li>
 * </ul>
 *
 * <p><strong>Phase 2 values (SCRUM-140)</strong> — emitted by 3 new consumers
 * in this PR :
 * <ul>
 *   <li>{@link #NEW_FOLLOWER} — emitted by {@code UserFollowedConsumer} ←
 *       {@code users.followed}. Public-profile follow auto-accepted.
 *       Destinataire = {@code followedId} ; {@code relatedUserId} = {@code followerId}.</li>
 *   <li>{@link #FOLLOW_REQUEST} — emitted by {@code UserFollowRequestedConsumer}
 *       ← {@code users.follow-requested}. Private-profile PENDING request.
 *       Destinataire = {@code followedId} ; {@code relatedUserId} = {@code followerId}.</li>
 *   <li>{@link #FOLLOW_ACCEPTED} — emitted by {@code UserFollowAcceptedConsumer}
 *       ← {@code users.follow-accepted}. <strong>Destinataire = {@code followerId}</strong>
 *       (l'initiateur initial, qui voit sa demande acceptée) ; {@code relatedUserId} =
 *       {@code followedId} (l'acceptant). <em>Inverser ces deux UUIDs serait une
 *       faute fonctionnelle bloquante</em> — cf. sentinel test
 *       {@code UserFollowAcceptedConsumerTest.destinationIsInitiatorNotAcceptor}.</li>
 * </ul>
 *
 * <p><strong>Phase 3 values (SCRUM-145)</strong> — <strong>RESERVED, not emitted phase 2</strong>.
 * Added to the enum + DB CHECK constraint dès phase 2 (Décision G) pour éviter
 * une 2e migration Flyway de widening en phase 3. SCRUM-145 livrera les 2 consumers
 * (purement additif côté schema) :
 * <ul>
 *   <li>{@link #COMMENT_MENTION} — will be emitted by a future
 *       {@code CommentMentionConsumer} ← {@code comments.created} (with
 *       mention parsing). NOT emitted in phase 2.</li>
 *   <li>{@link #NEW_COMMENT} — will be emitted by a future
 *       {@code NewCommentConsumer} ← {@code comments.created} (notifies
 *       event creator). NOT emitted in phase 2.</li>
 * </ul>
 *
 * <p>The DB CHECK constraint {@code notifications_type_check} on column
 * {@code type} is widened to the 9 values by
 * {@code V2__widen_notification_type_check.sql} (commit-atomic with this enum
 * — Décision F). Adding a value to this enum without bumping the migration
 * would fail at INSERT time with a CHECK violation — by design.
 */
public enum NotificationType {
    // Phase 1 (SCRUM-99)
    EVENT_UPDATED,
    EVENT_CANCELLED,
    EVENT_REMINDER,
    NEW_ATTENDEE,
    // Phase 2 (SCRUM-140) — emitted by 3 new consumers in this PR
    NEW_FOLLOWER,
    FOLLOW_REQUEST,
    FOLLOW_ACCEPTED,
    // Phase 3 (SCRUM-145) — RESERVED, not emitted phase 2
    COMMENT_MENTION,
    NEW_COMMENT
}
