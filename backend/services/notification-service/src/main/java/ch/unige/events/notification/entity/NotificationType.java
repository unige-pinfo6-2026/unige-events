package ch.unige.events.notification.entity;

/**
 * In-app notification kinds. Owned by notification-service — not in
 * {@code shared-domain-enums} because no other service consumes the enum
 * (cf. SCRUM-99 Décision C).
 *
 * <p><strong>Phase 1 values (SCRUM-99).</strong>
 * <ul>
 *   <li>{@link #EVENT_UPDATED} — an event the user is registered to has been
 *       modified ({@code EventService.update}).</li>
 *   <li>{@link #EVENT_CANCELLED} — an event the user is registered to has
 *       been cancelled ({@code EventService.cancel}).</li>
 *   <li>{@link #EVENT_REMINDER} — reserved for a future scheduler (24h
 *       before start). Not wired in phase 1.</li>
 *   <li>{@link #NEW_ATTENDEE} — a user signed up for an event the caller
 *       created (only {@code AttendanceStatus.ATTENDING} triggers it —
 *       Décision M).</li>
 * </ul>
 *
 * <p><strong>Phase 2 extension</strong> (SCRUM-140 / SCRUM-144 / SCRUM-145).
 * Five additional values will land in phase 2 alongside their respective
 * consumers and the matching CHECK widening in
 * {@code V2__widen_notification_type_check.sql}:
 * <ul>
 *   <li>{@code NEW_FOLLOWER} — public profile gets a new follower.</li>
 *   <li>{@code FOLLOW_REQUEST} — private profile receives a follow request.</li>
 *   <li>{@code FOLLOW_ACCEPTED} — a pending follow request was accepted.</li>
 *   <li>{@code COMMENT_MENTION} — the user was @-mentioned in a comment.</li>
 *   <li>{@code NEW_COMMENT} — a new comment was posted on the user's event.</li>
 * </ul>
 *
 * <p>The DB CHECK constraint on column {@code type} is locked to the four
 * phase 1 values until the V2 migration drop+recreates it with the wider
 * set. Adding a new enum value here without bumping the migration would
 * fail at insert time with a CHECK violation — by design.
 */
public enum NotificationType {
    EVENT_UPDATED,
    EVENT_CANCELLED,
    EVENT_REMINDER,
    NEW_ATTENDEE
}
