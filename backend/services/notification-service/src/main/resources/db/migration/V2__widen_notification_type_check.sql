-- V2__widen_notification_type_check.sql — SCRUM-140 (+ SCRUM-145 anticipation).
--
-- Widens the CHECK constraint on notifications.type from 4 values (phase 1, SCRUM-99)
-- to 9 values total :
--   - 4 phase 1 values (EVENT_UPDATED, EVENT_CANCELLED, EVENT_REMINDER, NEW_ATTENDEE)
--   - 3 phase 2 SCRUM-140 values (NEW_FOLLOWER, FOLLOW_REQUEST, FOLLOW_ACCEPTED)
--     emitted by the new consumers in notification-service.kafka.
--   - 2 phase 3 SCRUM-145 RESERVED values (COMMENT_MENTION, NEW_COMMENT)
--     added now (Décision G) to avoid a second Flyway widening migration when
--     SCRUM-145 ships. NOT emitted in phase 2 — no consumer in notification-service
--     produces them yet.
--
-- This migration is commit-atomic with the NotificationType.java enum extension
-- (Décision F). Persisting an enum value not covered by the CHECK fails at INSERT
-- with a constraint violation ; conversely, widening the CHECK without extending
-- the enum is dead code.
--
-- Immutable once pushed (cf. Flyway invariant — leçon SCRUM-139 / SCRUM-99 phase 1).

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;

ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (
    type IN (
        -- Phase 1 (SCRUM-99)
        'EVENT_UPDATED',
        'EVENT_CANCELLED',
        'EVENT_REMINDER',
        'NEW_ATTENDEE',
        -- Phase 2 (SCRUM-140) — emitted by 3 new consumers in this PR
        'NEW_FOLLOWER',
        'FOLLOW_REQUEST',
        'FOLLOW_ACCEPTED',
        -- Phase 3 (SCRUM-145) — RESERVED, not emitted phase 2 (cf. Décision G)
        'COMMENT_MENTION',
        'NEW_COMMENT'
    )
);
